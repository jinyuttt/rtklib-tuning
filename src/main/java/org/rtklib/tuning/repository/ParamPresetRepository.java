package org.rtklib.tuning.repository;

import org.rtklib.tuning.entity.ParamPresetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ParamPresetRepository extends JpaRepository<ParamPresetEntity, Long> {
    Optional<ParamPresetEntity> findByName(String name);
}