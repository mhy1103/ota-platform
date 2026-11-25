package com.ota.platform.notice;

import com.ota.platform.agent.Agent;
import com.ota.platform.agent.AgentRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 代理公告：管理员在后台发（发给全部代理或只发一级代理），开了二级代理的一级代理在代理后台给自己的二级代理发。
 * 代理后台查看（看过就记下），发的人能看到多少人看过
 */
@Service
public class NoticeService {

    public static final int TITLE_MAX = 60;
    public static final int BODY_MAX = 2000;

    private final NoticeRepo notices;
    private final NoticeReadRepo reads;
    private final AgentRepo agentRepo;

    public NoticeService(NoticeRepo notices, NoticeReadRepo reads, AgentRepo agentRepo) {
        this.notices = notices;
        this.reads = reads;
        this.agentRepo = agentRepo;
    }

    /** 列表的一条：发给多少个启用中的代理、多少看过了 */
    public record AdminView(Notice notice, long audience, long read) {}

    /** 代理后台的一条；fromParent 是上级代理发的 */
    public record AgentView(Long id, String title, String body, boolean pinned, boolean important, LocalDateTime at, boolean read,
                            boolean fromParent) {}

    /** 管理员发的公告 */
    public List<AdminView> adminList() {
        Set<Long> all = new HashSet<>(), top = new HashSet<>();
        for (Agent a : agentRepo.findAll()) {
            if (!Boolean.TRUE.equals(a.getEnabled())) continue;
            all.add(a.getId());
            if (!a.isSub()) top.add(a.getId());
        }
        Map<Long, Long> readAll = readCount(all), readTop = readCount(top);
        List<AdminView> out = new ArrayList<>();
        for (Notice n : notices.findAllByOrderByPinnedDescCreatedAtDesc()) {
            if (n.getOwnerAgentId() != null) continue;
            boolean onlyTop = Notice.TOP.equals(n.getAudience());
            long audience = onlyTop ? top.size() : all.size();
            out.add(new AdminView(n, audience, Math.min(audience, (onlyTop ? readTop : readAll).getOrDefault(n.getId(), 0L))));
        }
        return out;
    }

    /** 一级代理发给自己二级代理的公告 */
    public List<AdminView> ownList(Agent p) {
        Set<Long> subs = new HashSet<>();
        for (Agent s : agentRepo.findByParentIdOrderByCreatedAtDesc(p.getId())) if (Boolean.TRUE.equals(s.getEnabled())) subs.add(s.getId());
        Map<Long, Long> read = readCount(subs);
        List<AdminView> out = new ArrayList<>();
        for (Notice n : notices.findAllByOrderByPinnedDescCreatedAtDesc()) {
            if (!p.getId().equals(n.getOwnerAgentId())) continue;
            out.add(new AdminView(n, subs.size(), Math.min(subs.size(), read.getOrDefault(n.getId(), 0L))));
        }
        return out;
    }

    private Map<Long, Long> readCount(Set<Long> agentIds) {
        Map<Long, Long> m = new HashMap<>();
        if (!agentIds.isEmpty()) for (Object[] r : reads.countByNotice(agentIds)) m.put((Long) r[0], (Long) r[1]);
        return m;
    }

    /** 这个代理能看到的：一级代理看管理员发的；二级代理看管理员发给全部代理的和 TA 的上级发的 */
    public List<AgentView> forAgent(Agent a) {
        Set<Long> seen = new HashSet<>();
        for (NoticeRead r : reads.findByAgentId(a.getId())) seen.add(r.getNoticeId());
        List<AgentView> out = new ArrayList<>();
        for (Notice n : notices.findAllByOrderByPinnedDescCreatedAtDesc()) {
            boolean fromParent = n.getOwnerAgentId() != null;
            if (fromParent ? !a.isSub() || !n.getOwnerAgentId().equals(a.getParentId()) : a.isSub() && !n.isForSubs()) continue;
            out.add(new AgentView(n.getId(), n.getTitle(), n.getBody(), n.isPinned(), n.isImportant(), n.getCreatedAt(), seen.contains(n.getId()), fromParent));
        }
        return out;
    }

    public long unread(Agent a) {
        return forAgent(a).stream().filter(v -> !v.read()).count();
    }

    /** 概览上横幅显示的那条：没看过的里面，重要的、置顶的优先，再按时间 */
    public AgentView banner(Agent a) {
        return forAgent(a).stream().filter(v -> !v.read())
                .min(Comparator.comparing((AgentView v) -> !v.important()).thenComparing(v -> !v.pinned())
                        .thenComparing(AgentView::at, Comparator.reverseOrder()))
                .orElse(null);
    }

    /** 记为看过：只记这个代理能看到的 */
    @Transactional
    public void markRead(Agent a, Collection<Long> ids) {
        Set<Long> visible = new HashSet<>();
        for (AgentView v : forAgent(a)) if (!v.read()) visible.add(v.id());
        for (Long id : new LinkedHashSet<>(ids)) if (id != null && visible.contains(id)) reads.save(new NoticeRead(id, a.getId()));
    }

    /** 标题、内容不合规时返回原因 */
    public static String validate(String title, String body) {
        String t = title == null ? "" : title.trim(), b = body == null ? "" : body.trim();
        if (t.isEmpty()) return "标题不能空着";
        if (t.length() > TITLE_MAX) return "标题最多 " + TITLE_MAX + " 个字";
        if (b.isEmpty()) return "内容不能空着";
        if (b.length() > BODY_MAX) return "内容最多 " + BODY_MAX + " 个字";
        return null;
    }

    /** 管理员发 */
    public Notice create(String title, String body, boolean pinned, String level, String audience, String by) {
        Notice n = new Notice(title.trim(), body.trim(), pinned, level, by);
        n.setAudience(audience);
        return notices.save(n);
    }

    /** 一级代理发给自己的二级代理 */
    public Notice createOwn(Agent p, String title, String body, boolean pinned, String level) {
        Notice n = new Notice(title.trim(), body.trim(), pinned, level, "代理：" + p.getName());
        n.setOwnerAgentId(p.getId());
        return notices.save(n);
    }

    public Optional<Notice> update(Long id, String title, String body, boolean pinned, String level, String audience) {
        return admins(id).map(n -> {
            n.edit(title.trim(), body.trim(), pinned, level);
            if (audience != null) n.setAudience(audience);
            return notices.save(n);
        });
    }

    public Optional<Notice> pin(Long id) {
        return admins(id).map(this::flipPin);
    }

    @Transactional
    public Optional<Notice> delete(Long id) {
        Optional<Notice> n = admins(id);
        n.ifPresent(this::remove);
        return n;
    }

    /** 一级代理改、置顶、删自己发的；不是 TA 的当作没有 */
    public Optional<Notice> updateOwn(Agent p, Long id, String title, String body, boolean pinned, String level) {
        return own(p, id).map(n -> {
            n.edit(title.trim(), body.trim(), pinned, level);
            return notices.save(n);
        });
    }

    public Optional<Notice> pinOwn(Agent p, Long id) {
        return own(p, id).map(this::flipPin);
    }

    @Transactional
    public Optional<Notice> deleteOwn(Agent p, Long id) {
        Optional<Notice> n = own(p, id);
        n.ifPresent(this::remove);
        return n;
    }

    /** 删代理时：TA 发的公告、TA 们看过的记录一起删 */
    @Transactional
    public void forget(Collection<Long> agentIds) {
        if (agentIds.isEmpty()) return;
        for (Notice n : notices.findAllByOrderByPinnedDescCreatedAtDesc()) {
            if (n.getOwnerAgentId() != null && agentIds.contains(n.getOwnerAgentId())) remove(n);
        }
        reads.deleteByAgents(agentIds);
    }

    private Optional<Notice> admins(Long id) {
        return notices.findById(id).filter(n -> n.getOwnerAgentId() == null);
    }

    private Optional<Notice> own(Agent p, Long id) {
        return notices.findById(id == null ? -1L : id).filter(n -> p.getId().equals(n.getOwnerAgentId()));
    }

    private Notice flipPin(Notice n) {
        n.setPinned(!n.isPinned());
        return notices.save(n);
    }

    private void remove(Notice n) {
        reads.deleteByNotice(n.getId());
        notices.delete(n);
    }
}
