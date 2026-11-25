package com.ota.platform.app;

import com.ota.platform.admin.AdminUserService;
import com.ota.platform.auth.ApiTokenRepository;
import com.ota.platform.codes.ActivationRecordRepo;
import com.ota.platform.ota.LaunchErrorService;
import com.ota.platform.ota.ReleaseStatsService;
import com.ota.platform.ota.TestDeviceService;
import com.ota.platform.release.DeploymentRepository;
import com.ota.platform.release.Release;
import com.ota.platform.release.ReleaseFileRepository;
import com.ota.platform.release.ReleaseRepository;
import com.ota.platform.stats.DeviceSeenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * 删除整个 App：渠道、发布记录、版本、这个 App 的激活记录、令牌、设备统计一起删。
 * 版本文件按内容共用，不在这里删，到「系统配置 → 存储」清理没有引用的文件。口令和代理账号所有 App 通用，保留。
 */
@Service
public class AppDeletionService {

    private final OtaAppRepository apps;
    private final AppService appService;
    private final ChannelRepository channels;
    private final DeploymentRepository deployments;
    private final ReleaseRepository releases;
    private final ReleaseFileRepository releaseFiles;
    private final ActivationRecordRepo records;
    private final ApiTokenRepository tokens;
    private final DeviceSeenRepository devices;
    private final TestDeviceService testDevices;
    private final ReleaseStatsService releaseStats;
    private final LaunchErrorService launchErrors;
    private final AdminUserService adminUsers;
    private final com.ota.platform.release.DeployEventRepository deployEvents;
    private final com.ota.platform.installer.InstallerService installers;
    private final com.ota.platform.webhook.NotifyDeliveryRepo notifyDeliveries;
    private final AppIcons appIcons;
    private final java.util.List<AppPurge> purges;

    public AppDeletionService(OtaAppRepository apps, AppService appService, ChannelRepository channels,
                              DeploymentRepository deployments, ReleaseRepository releases, ReleaseFileRepository releaseFiles,
                              ActivationRecordRepo records, ApiTokenRepository tokens,
                              DeviceSeenRepository devices, TestDeviceService testDevices, ReleaseStatsService releaseStats,
                              LaunchErrorService launchErrors, AdminUserService adminUsers,
                              com.ota.platform.release.DeployEventRepository deployEvents,
                              com.ota.platform.installer.InstallerService installers,
                              com.ota.platform.webhook.NotifyDeliveryRepo notifyDeliveries, AppIcons appIcons,
                              java.util.List<AppPurge> purges) {
        this.appIcons = appIcons;
        this.purges = purges;
        this.installers = installers;
        this.notifyDeliveries = notifyDeliveries;
        this.apps = apps;
        this.appService = appService;
        this.channels = channels;
        this.deployments = deployments;
        this.releases = releases;
        this.releaseFiles = releaseFiles;
        this.records = records;
        this.tokens = tokens;
        this.devices = devices;
        this.testDevices = testDevices;
        this.releaseStats = releaseStats;
        this.launchErrors = launchErrors;
        this.adminUsers = adminUsers;
        this.deployEvents = deployEvents;
    }

    public record Deleted(String slug, int releases, int records, int tokens) {}

    @Transactional
    public Deleted delete(OtaApp app) {
        Long appId = app.getId();
        deployEvents.deleteByAppId(appId);
        installers.list(app).forEach(installers::delete);
        for (Channel c : channels.findByAppIdOrderByCreatedAtAsc(appId)) {
            deployments.deleteByChannelId(c.getId());
            channels.delete(c);
        }
        List<Release> rels = releases.findByAppIdOrderByCreatedAtDesc(appId);
        for (Release r : rels) releaseFiles.deleteByReleaseId(r.getId());
        releaseStats.drop(rels.stream().map(Release::getId).toList());
        releases.deleteAll(rels);
        int recordCount = records.deleteByAppId(appId);
        int tokenCount = tokens.deleteByAppId(appId);
        devices.deleteByAppId(appId);
        for (AppPurge p : purges) p.purge(appId);
        notifyDeliveries.deleteByAppId(appId);
        appIcons.purge(appId);
        testDevices.dropApp(appId);
        launchErrors.dropApp(appId);
        adminUsers.dropApp(appId);
        apps.delete(app);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                appService.evict(appId);
            }
        });
        return new Deleted(app.getSlug(), rels.size(), recordCount, tokenCount);
    }
}
