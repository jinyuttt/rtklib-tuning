package org.rtklib.tuning.repository;

import org.rtklib.tuning.entity.BaseStation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BaseStationRepository extends JpaRepository<BaseStation, Long> {
    List<BaseStation> findByEnabledTrueOrderByName();
    List<BaseStation> findByNameContainingIgnoreCase(String name);
}