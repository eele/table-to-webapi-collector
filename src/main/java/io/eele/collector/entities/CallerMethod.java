package io.eele.collector.entities;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Data
public class CallerMethod {
    private String className;
    private String methodName;
    private List<String> tableNames;
    private String sql;
    private String uri;
    private List<CallerMethod> callerMethodList = new ArrayList<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CallerMethod that = (CallerMethod) o;
        return className.equals(that.className) &&
                methodName.equals(that.methodName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(className, methodName);
    }
}
