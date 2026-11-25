package com.ota.platform.codes;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/** 带 All 的是所有归了代理的口令，其余的是 ids 这些口令 */
public interface ActivationDayRepo extends JpaRepository<ActivationDay, Long> {

    boolean existsByRecordIdAndDay(Long recordId, LocalDate day);

    /** 从哪天开始有记录 */
    @Query("select min(d.day) from ActivationDay d")
    LocalDate firstDay();

    /** [口令 ID, 哪天, 那天打开过的激活数] */
    @Query("select d.codeId, d.day, count(d) from ActivationDay d where d.codeId in :ids and d.day between :from and :to group by d.codeId, d.day")
    List<Object[]> activeByCode(@Param("ids") Collection<Long> ids, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select d.codeId, d.day, count(d) from ActivationDay d, ActivationCode c where c.id = d.codeId and c.agentId is not null"
            + " and d.day between :from and :to group by d.codeId, d.day")
    List<Object[]> activeByCodeAll(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** [口令 ID, 激活的那天, 激活后第几天, 那天打开过的激活数]：只要 ages 这几天 */
    @Query("select d.codeId, d.actDay, d.age, count(d) from ActivationDay d where d.codeId in :ids and d.actDay between :from and :to"
            + " and d.age in :ages group by d.codeId, d.actDay, d.age")
    List<Object[]> retainedByCode(@Param("ids") Collection<Long> ids, @Param("from") LocalDate from, @Param("to") LocalDate to,
                                  @Param("ages") Collection<Integer> ages);

    @Query("select d.codeId, d.actDay, d.age, count(d) from ActivationDay d, ActivationCode c where c.id = d.codeId and c.agentId is not null"
            + " and d.actDay between :from and :to and d.age in :ages group by d.codeId, d.actDay, d.age")
    List<Object[]> retainedByCodeAll(@Param("from") LocalDate from, @Param("to") LocalDate to, @Param("ages") Collection<Integer> ages);

    /** 同上，不分是不是代理的口令；按 stat_day 先圈住范围（dayFrom..dayTo 要盖住 from + ages），走 stat_day 的索引，不扫整张表 */
    @Query("select d.codeId, d.actDay, d.age, count(d) from ActivationDay d where d.day between :dayFrom and :dayTo"
            + " and d.actDay between :from and :to and d.age in :ages group by d.codeId, d.actDay, d.age")
    List<Object[]> retainedBetween(@Param("dayFrom") LocalDate dayFrom, @Param("dayTo") LocalDate dayTo,
                                   @Param("from") LocalDate from, @Param("to") LocalDate to, @Param("ages") Collection<Integer> ages);

    @Modifying
    @Transactional
    @Query("delete from ActivationDay d where d.day < :before")
    int deleteBefore(@Param("before") LocalDate before);

    @Modifying
    @Transactional
    @Query("delete from ActivationDay d where d.appId = :appId")
    int deleteByAppId(@Param("appId") Long appId);
}
