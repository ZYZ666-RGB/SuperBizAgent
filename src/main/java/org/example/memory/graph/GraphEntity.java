package org.example.memory.graph;

import java.util.Objects;

public class GraphEntity {

    private String name;
    private String type;

    public GraphEntity() {
    }

    public GraphEntity(String name, String type) {
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String key() {
        return type + ":" + name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GraphEntity that)) {
            return false;
        }
        return Objects.equals(name, that.name) && Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type);
    }
}
