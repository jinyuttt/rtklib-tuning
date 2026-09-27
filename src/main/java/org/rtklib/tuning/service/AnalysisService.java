package org.rtklib.tuning.service;

import org.rtklib.tuning.dto.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalysisService {

    /** Detect jump events from solution epochs */
    public List<JumpEventDto> detectJumps(List<SolutionEpochDto> solutions, List<EpochDataDto> epochData,
                                          List<RtcmMessageDto> rtcmMessages, int contextWindow) {
        List<JumpEventDto> jumps = new ArrayList<>();
        if (solutions.size() < 2) return jumps;

        double prevLat = solutions.get(0).getLatitude();
        double prevLon = solutions.get(0).getLongitude();
        double prevHgt = solutions.get(0).getHeight();
        int prevMode = solutions.get(0).getFixMode();

        for (int i = 1; i < solutions.size(); i++) {
            SolutionEpochDto curr = solutions.get(i);
            int currMode = curr.getFixMode();

            double dLat = (curr.getLatitude() - prevLat) * 111320.0;
            double dLon = (curr.getLongitude() - prevLon) * 111320.0 * Math.cos(Math.toRadians(curr.getLatitude()));
            double dHgt = curr.getHeight() - prevHgt;
            double jumpDist = Math.sqrt(dLat * dLat + dLon * dLon + dHgt * dHgt);

            boolean modeDowngrade = (prevMode >= 10 && currMode < 10) || (prevMode >= 5 && currMode < 5);
            boolean largeJump = jumpDist > 5.0;

            if (modeDowngrade || largeJump) {
                JumpEventDto event = new JumpEventDto();
                event.setTimestamp(curr.getTimestamp());
                event.setFromMode(prevMode);
                event.setToMode(currMode);
                event.setCoordinateJump(jumpDist);

                event.setSolutionContext(sliceAround(solutions, i, contextWindow));
                event.setContextBefore(sliceAround(epochData, i, contextWindow));
                event.setContextAfter(sliceAfter(epochData, i, contextWindow));
                event.setRtcmContext(findNearbyRtcm(rtcmMessages, curr.getTimestamp(), contextWindow * 1000L));

                attribute(event, epochData.isEmpty() ? null : epochData.get(Math.min(i, epochData.size() - 1)));
                jumps.add(event);
            }

            prevLat = curr.getLatitude();
            prevLon = curr.getLongitude();
            prevHgt = curr.getHeight();
            prevMode = currMode;
        }
        return jumps;
    }

    /** Run attribution rules on a jump event */
    public void attribute(JumpEventDto event, EpochDataDto epoch) {
        List<String> causes = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        if (epoch != null && epoch.getDiffAge() > 30) {
            causes.add("差分龄期过大(" + String.format("%.1f", epoch.getDiffAge()) + "s)");
            suggestions.add("检查差分数据链路/UHF通信质量，或临时放宽 maxage 参数");
        }
        if (event.getFromMode() >= 10 && event.getToMode() < 10) {
            causes.add("模糊度固定丢失: " + event.getFromModeName() + "→" + event.getToModeName());
            if (epoch != null && epoch.getSatCount() < 8) {
                suggestions.add("可用卫星数仅" + epoch.getSatCount() + "颗，不足维持Fixed解；检查观测环境");
            }
            suggestions.add("检查 Ratio 是否持续下降，可能存在多路径或信噪比退化");
        }
        if (event.getCoordinateJump() > 10) {
            causes.add("坐标突变 " + String.format("%.2f", event.getCoordinateJump()) + "m");
            suggestions.add("该历元观测质量可能劣化，检查对应时刻的天空图");
        }

        if (causes.isEmpty()) causes.add("未知原因");
        if (suggestions.isEmpty()) suggestions.add("建议查看该时段的差分龄期与卫星信噪比时序");

        event.setAttribution(String.join("; ", causes));
        event.setSuggestion(String.join("; ", suggestions));
    }

    /** Simulate filter: apply elevation mask and SNR mask, return which sats would be removed */
    public Map<String, Boolean> simulateFilter(EpochDataDto epoch, int elevMask, int snrMask, List<String> exclSats) {
        Map<String, Boolean> filtered = new LinkedHashMap<>();
        if (epoch.getElevationData() != null) {
            for (Map.Entry<String, Double> e : epoch.getElevationData().entrySet()) {
                String sat = e.getKey();
                boolean keep = true;
                if (e.getValue() < elevMask) keep = false;
                if (epoch.getSnrData() != null && epoch.getSnrData().containsKey(sat)) {
                    if (epoch.getSnrData().get(sat) < snrMask) keep = false;
                }
                if (exclSats != null && exclSats.contains(sat)) keep = false;
                filtered.put(sat, keep);
            }
        }
        return filtered;
    }

    /** Evaluate parameter combination: count how many epochs maintain Fix */
    public double evaluateFixRate(List<SolutionEpochDto> solutions) {
        long total = solutions.size();
        long fix = solutions.stream().filter(s -> s.getFixMode() >= 10).count();
        long floatCount = solutions.stream().filter(s -> s.getFixMode() == 5 || s.getFixMode() == 11).count();
        return total > 0 ? (double) fix / total : 0;
    }

    /** Build epoch-level data from solution + RTCM message analysis */
    public List<EpochDataDto> buildEpochData(List<SolutionEpochDto> solutions, List<RtcmMessageDto> rtcmMessages) {
        List<EpochDataDto> epochs = new ArrayList<>();
        Map<Long, List<RtcmMessageDto>> rtcmByTime = rtcmMessages.stream()
            .collect(Collectors.groupingBy(m -> (m.getTimestamp() / 1000) * 1000));

        for (SolutionEpochDto sol : solutions) {
            EpochDataDto e = new EpochDataDto();
            e.setTimestamp(sol.getTimestamp());
            e.setDiffAge(sol.getDiffAge());

            long key = (sol.getTimestamp() / 1000) * 1000;
            List<RtcmMessageDto> near = rtcmByTime.get(key);
            e.setSatCount(near != null ? near.size() : 0);

            epochs.add(e);
        }
        return epochs;
    }

    private <T> List<T> sliceAround(List<T> list, int idx, int window) {
        int from = Math.max(0, idx - window);
        return new ArrayList<>(list.subList(from, Math.min(list.size(), idx + 1)));
    }

    private <T> List<T> sliceAfter(List<T> list, int idx, int window) {
        int to = Math.min(list.size(), idx + window + 1);
        return new ArrayList<>(list.subList(Math.max(0, idx), to));
    }

    private List<RtcmMessageDto> findNearbyRtcm(List<RtcmMessageDto> msgs, long timestamp, long windowMs) {
        return msgs.stream()
            .filter(m -> Math.abs(m.getTimestamp() - timestamp) < windowMs)
            .collect(Collectors.toList());
    }
}