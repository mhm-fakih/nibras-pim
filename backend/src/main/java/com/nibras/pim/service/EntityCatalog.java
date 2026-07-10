package com.nibras.pim.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Registry of all CRUD services keyed by their URL slug, used by RSS + calendar. */
@Component
public class EntityCatalog {

    private final Map<String, AbstractCrudService<?, ?>> byName;

    public EntityCatalog(List<AbstractCrudService<?, ?>> services) {
        this.byName = services.stream()
                .collect(Collectors.toMap(AbstractCrudService::name, Function.identity()));
    }

    public Optional<AbstractCrudService<?, ?>> find(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    public List<AbstractCrudService<?, ?>> all() {
        return List.copyOf(byName.values());
    }

    public List<String> names() {
        return byName.keySet().stream().sorted().toList();
    }
}
