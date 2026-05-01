package com.epm.template.domain.port.in;

import com.epm.template.domain.model.Example;

/**
 * Driving port: defines what this service can do from the outside world's perspective.
 *
 * <p>Controllers, event consumers, and CLI runners call this interface.
 * They never depend on the use case implementation directly.
 */
public interface CreateExampleUseCase {

    Example create(String name);
}
