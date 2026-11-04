package com.nemosdream.be.bandit.dto;

public record BanditSnapshot(int pitcherId, double priorNApps, Double priorReward, Double priorWpaStd,
                             Double priorBbRate, Double priorSoRate, Double priorHrRate, Double priorAvgPa) {}
