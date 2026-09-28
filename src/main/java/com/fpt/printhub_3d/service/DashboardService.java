package com.fpt.printhub_3d.service;

import com.fpt.printhub_3d.dto.dashboard.DashboardResponseDTO;
import com.fpt.printhub_3d.entity.User;

public interface DashboardService {
    DashboardResponseDTO getDashboard(User user, boolean all);
}
