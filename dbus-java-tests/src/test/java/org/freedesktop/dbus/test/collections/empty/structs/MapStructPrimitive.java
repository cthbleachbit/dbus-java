package org.freedesktop.dbus.test.collections.empty.structs;

import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.annotations.Position;

import java.util.Map;
import java.util.stream.Collectors;

public final class MapStructPrimitive extends Struct implements IEmptyCollectionStruct<Map<String, Integer>> {

    @Position(0)
    private final Map<String, Integer> map;

    @Position(1)
    private final String validationValue;

    public MapStructPrimitive(Map<String, Integer> _map, String _validationValue) {
        this.map = _map;
        this.validationValue = _validationValue;
    }

    @Override
    public Map<String, Integer> getValue() {
        return map;
    }

    @Override
    public String getValidationValue() {
        return validationValue;
    }

    @Override
    public String getStringTestValue() {
        String values = map.entrySet().stream()
                .map(e -> String.format("%s:%s", e.getKey(), e.getValue()))
                .collect(Collectors.joining(","));
        return String.format("{%s}", values);
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

}
