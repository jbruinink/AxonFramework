package org.axonframework.commandhandling.annotation;

import org.axonframework.commandhandling.CommandMessage;

public interface EntityAccessor {

    Object getInstance(Object aggregateRoot, CommandMessage<?> commandMessage) throws IllegalAccessException;

	Class<?> getEntityType();
    
}
