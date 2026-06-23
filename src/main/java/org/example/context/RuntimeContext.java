package org.example.context;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.UUID;

public class RuntimeContext {

    private String requestId = UUID.randomUUID().toString();
    private String userId;
    private String sessionId;
    private String taskId;
    private String agentId;
    private String query;
    private String namespace = "default";
    private ZoneId timezone = ZoneId.systemDefault();
    private Locale locale = Locale.getDefault();
    private LocalDateTime now = LocalDateTime.now();

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public ZoneId getTimezone() {
        return timezone;
    }

    public void setTimezone(ZoneId timezone) {
        this.timezone = timezone;
    }

    public Locale getLocale() {
        return locale;
    }

    public void setLocale(Locale locale) {
        this.locale = locale;
    }

    public LocalDateTime getNow() {
        return now;
    }

    public void setNow(LocalDateTime now) {
        this.now = now;
    }
}
