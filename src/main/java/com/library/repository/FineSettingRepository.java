package com.library.repository;

import com.library.entity.FineSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FineSettingRepository extends JpaRepository<FineSetting, Long> {
}
