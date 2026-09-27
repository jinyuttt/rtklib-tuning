package org.rtklib.tuning.repository;

import org.rtklib.tuning.entity.TuningConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TuningConfigRepository extends JpaRepository<TuningConfigEntity, Long> {
    Optional<TuningConfigEntity> findByStationId(Long stationId);
}