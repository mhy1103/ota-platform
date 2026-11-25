package com.ota.platform.codes;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ActivationRecordRepo extends JpaRepository<ActivationRecord, Long>, JpaSpecificationExecutor<ActivationRecord> {
    /** 同一台设备、同一个口令在每个 App 里各记一条 */
    Optional<ActivationRecord> findByAppIdAndDeviceIdAndCodeId(Long appId, String deviceId, Long codeId);

    /** 这台设备在这个 App 里最早确认的激活 */
    Optional<ActivationRecord> findFirstByAppIdAndDeviceIdAndConfirmedTrueOrderByIdAsc(Long appId, String deviceId);

    boolean existsByAppIdAndDeviceIdAndConfirmedTrueAndIdNot(Long appId, String deviceId, Long id);

    /** 别的 App 里这个设备号最早算过的激活（同一个开发者账号的 App 设备号一样） */
    Optional<ActivationRecord> findFirstByDeviceIdAndAppIdNotAndConfirmedTrueOrderByIdAsc(String deviceId, Long appId);

    /** 这个设备指纹最早算过的、不是自己的那条有效激活（跨 App、跨账号认同一台手机） */
    @Query("select r from ActivationRecord r where r.fingerprint = :fp and r.confirmed = true and r.id <> :id order by r.id asc limit 1")
    Optional<ActivationRecord> findFirstByFingerprintAndConfirmedTrueAndIdNot(@Param("fp") String fp, @Param("id") Long id);

    /** 补写设备指纹（已经有的不改）；老用户升级后 B 包校验时补上，之后别的 App 激活才认得出 */
    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("update ActivationRecord r set r.fingerprint = :fp where r.id = :id and r.fingerprint is null")
    int setFingerprintIfMissing(@Param("id") Long id, @Param("fp") String fp);

    /** 记下这台设备今天活跃（只改和今天不一样的，省写） */
    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("update ActivationRecord r set r.lastActive = :day where r.id = :id and (r.lastActive is null or r.lastActive <> :day)")
    int touchLastActive(@Param("id") Long id, @Param("day") java.time.LocalDate day);

    /**
     * 这个 App 里太久没打开、指纹还在的设备：最近活跃早于 before；还没记过活跃的按确认时间（没确认按提交时间）算，
     * beforeAt 是 before 那天 0 点。刚确认、日活还没记上的不会算进来
     */
    @Query("select r.id from ActivationRecord r where r.appId = :appId and r.fingerprint is not null and (r.lastActive < :before"
            + " or (r.lastActive is null and coalesce(r.confirmedAt, r.activatedAt) < :beforeAt))")
    List<Long> staleFingerprintIds(@Param("appId") Long appId, @Param("before") java.time.LocalDate before,
                                   @Param("beforeAt") LocalDateTime beforeAt);

    /** 清掉这些设备的指纹（不再参与跨 App 去重）；按主键清，清之前再判断一遍，这期间又打开过的不清 */
    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("update ActivationRecord r set r.fingerprint = null where r.id in :ids and r.fingerprint is not null and (r.lastActive < :before"
            + " or (r.lastActive is null and coalesce(r.confirmedAt, r.activatedAt) < :beforeAt))")
    int clearFingerprints(@Param("ids") Collection<Long> ids, @Param("before") java.time.LocalDate before,
                          @Param("beforeAt") LocalDateTime beforeAt);

    /** 只改还没确认的记录。App 重试或并发确认时只有一次返回 1 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ActivationRecord r set r.confirmed = true, r.confirmedAt = :now where r.id = :id and (r.confirmed is null or r.confirmed = false)")
    int markConfirmed(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** 判为确认太快：只标还没确认、也没标过的，并发时只有一次返回 1 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ActivationRecord r set r.fastMs = :ms where r.id = :id and r.fastMs is null and (r.confirmed is null or r.confirmed = false)")
    int markFast(@Param("id") Long id, @Param("ms") int ms);

    /** 判为重复激活：只标还没确认、也没标过的 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ActivationRecord r set r.dupOf = :of, r.dupBy = :by where r.id = :id and r.dupOf is null and r.fastMs is null "
            + "and (r.confirmed is null or r.confirmed = false)")
    int markDup(@Param("id") Long id, @Param("of") Long of, @Param("by") String by);

    List<ActivationRecord> findTop8ByAppIdOrderByActivatedAtDesc(Long appId);
    long countByAppId(Long appId);

    /** 还在等确认的（确认太快、重复激活的不算待确认，下同） */
    @Query("select count(r) from ActivationRecord r where r.appId = :appId and (r.confirmed is null or r.confirmed = false) and r.fastMs is null and r.dupOf is null")
    long countPendingByApp(@Param("appId") Long appId);
    long countByConfirmedTrue();
    long countByAppIdAndConfirmedTrue(Long appId);
    long countByAppIdAndConfirmedTrueAndConfirmedAtGreaterThanEqual(Long appId, LocalDateTime since);
    long countByAppIdAndActivatedAtGreaterThanEqual(Long appId, LocalDateTime since);

    /** [codeId, 待确认数] */
    @Query("select r.codeId, count(r) from ActivationRecord r where r.appId = :appId and (r.confirmed is null or r.confirmed = false) and r.fastMs is null and r.dupOf is null group by r.codeId")
    List<Object[]> countPendingGroupByCode(@Param("appId") Long appId);

    /** [codeId, 最近提交时间] */
    @Query("select r.codeId, max(r.activatedAt) from ActivationRecord r where r.appId = :appId group by r.codeId")
    List<Object[]> lastActivityGroupByCode(@Param("appId") Long appId);

    List<ActivationRecord> findByCodeIdIn(Collection<Long> codeIds);

    /** [codeId, 待确认数]，只查这些口令 */
    @Query("select r.codeId, count(r) from ActivationRecord r where r.codeId in :ids and (r.confirmed is null or r.confirmed = false) and r.fastMs is null and r.dupOf is null group by r.codeId")
    List<Object[]> countPendingByCodeIds(@Param("ids") Collection<Long> ids);

    /** [codeId, 最近提交时间]，只查这些口令 */
    @Query("select r.codeId, max(r.activatedAt) from ActivationRecord r where r.codeId in :ids group by r.codeId")
    List<Object[]> lastActivityByCodeIds(@Param("ids") Collection<Long> ids);

    /** [codeId, 有效激活数] */
    @Query("select r.codeId, count(r) from ActivationRecord r where r.confirmed = true group by r.codeId")
    List<Object[]> countConfirmedGroupByCode();

    /** [codeId, since 之后的有效激活数] */
    @Query("select r.codeId, count(r) from ActivationRecord r where r.confirmed = true and r.confirmedAt >= :since group by r.codeId")
    List<Object[]> countConfirmedSinceGroupByCode(@Param("since") LocalDateTime since);

    /** 截至 cutoff 已确认的数量（代理端看到的数） */
    @Query("select count(r) from ActivationRecord r where r.codeId in :ids and r.confirmed = true and (r.confirmedAt is null or r.confirmedAt <= :cutoff)")
    long countConfirmedAsOf(@Param("ids") Collection<Long> ids, @Param("cutoff") LocalDateTime cutoff);

    /** 概览：since 之后被用过（提交过）的口令有几个 */
    @Query("select count(distinct r.codeId) from ActivationRecord r where r.appId = :appId and r.activatedAt >= :since")
    long countCodesUsedSince(@Param("appId") Long appId, @Param("since") LocalDateTime since);

    /** 概览的激活趋势：since 之后确认的时间 */
    /** [口令 ID, 确认时间]，所有 App，代理统计用 */
    @Query("select r.codeId, r.confirmedAt from ActivationRecord r where r.confirmed = true and r.confirmedAt >= :since")
    List<Object[]> confirmedSince(@Param("since") LocalDateTime since);

    /** [提交时间, 确认时间, 是否确认, App ID]，代理详情按月统计用 */
    @Query("select r.activatedAt, r.confirmedAt, r.confirmed, r.appId from ActivationRecord r where r.codeId in :ids and r.activatedAt >= :since")
    List<Object[]> activityForCodes(@Param("ids") Collection<Long> ids, @Param("since") LocalDateTime since);

    List<ActivationRecord> findTop12ByCodeIdInOrderByActivatedAtDesc(Collection<Long> codeIds);

    /** [口令 ID, 口令, 有效激活数]，since 之后这个 App 里激活最多的 */
    @Query("select r.codeId, r.codeText, count(r) from ActivationRecord r where r.appId = :appId and r.confirmed = true and r.confirmedAt >= :since "
            + "group by r.codeId, r.codeText order by count(r) desc, r.codeId asc")
    List<Object[]> topCodes(@Param("appId") Long appId, @Param("since") LocalDateTime since, org.springframework.data.domain.Pageable page);

    // ---------- 数据大屏：一条查询管多个 App（按 App 分组），几十个 App 也不用逐个去查 ----------

    /** [App ID, 确认时间, 口令 ID]，since 之后确认的 */
    @Query("select r.appId, r.confirmedAt, r.codeId from ActivationRecord r where r.appId in :ids and r.confirmed = true and r.confirmedAt >= :since")
    List<Object[]> confirmedTimesIn(@Param("ids") Collection<Long> ids, @Param("since") LocalDateTime since);

    /** [App ID, 确认时间]，[from, to) 之间确认的 */
    @Query("select r.appId, r.confirmedAt from ActivationRecord r where r.appId in :ids and r.confirmed = true "
            + "and r.confirmedAt >= :from and r.confirmedAt < :to")
    List<Object[]> confirmedTimesBetween(@Param("ids") Collection<Long> ids, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** [App ID, 年, 月, 日, 时, 数量]：[from, to) 之间确认的，按服务器时区的钟点合并（行数和记录多少无关） */
    @Query("select r.appId, year(r.confirmedAt), month(r.confirmedAt), day(r.confirmedAt), hour(r.confirmedAt), count(r) from ActivationRecord r "
            + "where r.appId in :ids and r.confirmed = true and r.confirmedAt >= :from and r.confirmedAt < :to "
            + "group by r.appId, year(r.confirmedAt), month(r.confirmedAt), day(r.confirmedAt), hour(r.confirmedAt)")
    List<Object[]> confirmedByHour(@Param("ids") Collection<Long> ids, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** [App ID, 数量]：before 之前确认的有效激活（没有确认时间的老记录也算） */
    @Query("select r.appId, count(r) from ActivationRecord r where r.appId in :ids and r.confirmed = true "
            + "and (r.confirmedAt is null or r.confirmedAt < :before) group by r.appId")
    List<Object[]> countConfirmedBefore(@Param("ids") Collection<Long> ids, @Param("before") LocalDateTime before);

    /** [App ID, 数量]：since 之后确认的有效激活 */
    @Query("select r.appId, count(r) from ActivationRecord r where r.appId in :ids and r.confirmed = true and r.confirmedAt >= :since group by r.appId")
    List<Object[]> countConfirmedByApp(@Param("ids") Collection<Long> ids, @Param("since") LocalDateTime since);

    /** [口令 ID, 次数]：since 之后提交的，按口令合计（加起来是提交次数，行数是用到的口令数） */
    @Query("select r.codeId, count(r) from ActivationRecord r where r.appId in :ids and r.activatedAt >= :since group by r.codeId")
    List<Object[]> submittedByCode(@Param("ids") Collection<Long> ids, @Param("since") LocalDateTime since);

    /** [from, to) 之间提交的次数 */
    @Query("select count(r) from ActivationRecord r where r.appId in :ids and r.activatedAt >= :from and r.activatedAt < :to")
    long countSubmittedBetween(@Param("ids") Collection<Long> ids, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** since 之后提交、还没确认的 */
    @Query("select count(r) from ActivationRecord r where r.appId in :ids and r.activatedAt >= :since and (r.confirmed is null or r.confirmed = false) and r.fastMs is null and r.dupOf is null")
    long countPendingSince(@Param("ids") Collection<Long> ids, @Param("since") LocalDateTime since);

    /** [代理 ID, 数量]：[from, to) 之间确认的有效激活，按口令所属的代理合计 */
    @Query("select c.agentId, count(r) from ActivationRecord r join ActivationCode c on c.id = r.codeId "
            + "where r.appId in :ids and r.confirmed = true and r.confirmedAt >= :from and r.confirmedAt < :to and c.agentId is not null "
            + "group by c.agentId")
    List<Object[]> confirmedByAgent(@Param("ids") Collection<Long> ids, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** [口令 ID, 口令, 有效激活数]，since 之后这些 App 里激活最多的 */
    @Query("select r.codeId, r.codeText, count(r) from ActivationRecord r where r.appId in :ids and r.confirmed = true and r.confirmedAt >= :since "
            + "group by r.codeId, r.codeText order by count(r) desc, r.codeId asc")
    List<Object[]> topCodesIn(@Param("ids") Collection<Long> ids, @Param("since") LocalDateTime since, org.springframework.data.domain.Pageable page);

    /** [有效激活数, 用到的口令数]，since 之后这些 App 里的 */
    @Query("select count(r), count(distinct r.codeId) from ActivationRecord r where r.appId in :ids and r.confirmed = true and r.confirmedAt >= :since")
    List<Object[]> codeUsageIn(@Param("ids") Collection<Long> ids, @Param("since") LocalDateTime since);

    /** 这些 App 里 since 之后提交的记录，新的在前 */
    @Query("select r from ActivationRecord r where r.appId in :ids and r.activatedAt >= :since order by r.activatedAt desc, r.id desc")
    List<ActivationRecord> recentIn(@Param("ids") Collection<Long> ids, @Param("since") LocalDateTime since, org.springframework.data.domain.Pageable page);

    /** [App ID, 有效激活数]，只算这些口令 */
    @Query("select r.appId, count(r) from ActivationRecord r where r.codeId in :ids and r.confirmed = true group by r.appId")
    List<Object[]> confirmedByAppForCodes(@Param("ids") Collection<Long> ids);

    @Query("select r.confirmedAt from ActivationRecord r where r.appId = :appId and r.confirmed = true and r.confirmedAt >= :since")
    List<LocalDateTime> confirmedTimesSince(@Param("appId") Long appId, @Param("since") LocalDateTime since);

    @Modifying
    @Query("delete from ActivationRecord r where r.appId = :appId")
    int deleteByAppId(@Param("appId") Long appId);

    @Modifying
    @Query("delete from ActivationRecord r where r.codeId in :ids")
    int deleteByCodeIdIn(@Param("ids") Collection<Long> ids);
}
