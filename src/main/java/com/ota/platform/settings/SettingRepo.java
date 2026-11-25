package com.ota.platform.settings;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingRepo extends JpaRepository<Setting, String> {
}
