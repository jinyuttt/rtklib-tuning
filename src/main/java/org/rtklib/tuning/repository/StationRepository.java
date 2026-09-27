package org.rtklib.tuning.repository;

import org.rtklib.tuning.entity.Station;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StationRepository extends JpaRepository<Station, Long> {
    List<Station> findByBaseStationIdOrderByName(Long baseStationId);
    List<Station> findByBaseStationIdAndEnabledTrueOrderByName(Long baseStationId);
    int countByBaseStationId(Long baseStationId);
}