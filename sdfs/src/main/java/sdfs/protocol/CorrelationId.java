package sdfs.protocol;

public final class CorrelationId {

    public final String id;

    public CorrelationId(String id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return id.equals(((CorrelationId) o).id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    public String toString() {
        return id;
    }
}
