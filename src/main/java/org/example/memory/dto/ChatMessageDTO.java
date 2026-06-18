package org.example.memory.dto;

public class ChatMessageDTO {

    private Long id;
    private String role;
    private String content;

    public ChatMessageDTO() {
    }

    public ChatMessageDTO(Long id, String role, String content) {
        this.id = id;
        this.role = role;
        this.content = content;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
