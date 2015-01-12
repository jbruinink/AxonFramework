package org.axonframework.commandhandling.annotation;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.ReflectionUtils;
import org.axonframework.common.property.Property;
import org.axonframework.common.property.PropertyAccessStrategy;
import org.axonframework.eventsourcing.annotation.AbstractAnnotatedEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EntityFieldAccessors {
	private static final Logger logger = LoggerFactory.getLogger(EntityFieldAccessors.class);
	
	private static final List<EntityFieldAccessorFactory> factories = Arrays.<EntityFieldAccessorFactory>asList(
			new SimpleEntityFieldAccessorFactory(), new EntityCollectionFieldAccessorFactory(),
			new EntityMapFieldAccessorFactory());
    
    public static EntityAccessor getInstance(EntityAccessor parent, Field field) {
    	ListIterator<EntityFieldAccessorFactory> it = factories.listIterator(factories.size());
    	while(it.hasPrevious()) {
    	EntityAccessor entityAccessor = it.previous().getInstance(parent, field);
    		if(entityAccessor != null) {
    			return entityAccessor;
    		}
    	}
    	return null;
    }
    
    public static void registerEntityFieldAccessorFactory(EntityFieldAccessorFactory factory) {
    	factories.add(factory);
    }
    
    public static interface EntityFieldAccessorFactory {
    	EntityAccessor getInstance(EntityAccessor parent, Field field);
    }

	private static class SimpleEntityFieldAccessorFactory implements EntityFieldAccessorFactory {

		@Override
		public EntityAccessor getInstance(EntityAccessor parent, Field field) {
			if (field.isAnnotationPresent(CommandHandlingMember.class)) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Field {}.{} is annotated with @CommandHandlingMember. "
                    		+ "Checking {} for Command Handlers",
                    		parent.getEntityType().getSimpleName(), field.getName(), 
                    		field.getType().getSimpleName()
                    );
                }
                return new SimpleEntityFieldAccessor(parent, field);
            }
			return null;
		}
	}
	
    private static class EntityCollectionFieldAccessorFactory implements EntityFieldAccessorFactory {

		@Override
		public EntityAccessor getInstance(EntityAccessor parent, Field field) {
			if (field.isAnnotationPresent(CommandHandlingMemberCollection.class)) {
                Class<?> targetType = parent.getEntityType();
                CommandHandlingMemberCollection annotation = field.getAnnotation(CommandHandlingMemberCollection.class);
				if (!Collection.class.isAssignableFrom(field.getType())) {
                    throw new AxonConfigurationException(String.format(
                            "Field %s.%s is annotated with @CommandHandlingMemberCollection, but the declared type of "
                                    + "the field is not assignable to java.util.Collection.",
                            targetType .getSimpleName(), field.getName()));
                }
                Class<?> entityType = determineEntityType(annotation.entityType(), field, 0);
                if(entityType == null) {
                	throw new AxonConfigurationException(String.format(
    		                "Field %s.%s is annotated with @CommandHandlingMemberCollection, but the entity"
    		                        + " type is not indicated on the annotation, "
    		                        + "nor can it be deduced from the generic parameters",
    		                targetType.getSimpleName(), field.getName()));
                }
                if (logger.isDebugEnabled()) {
                    logger.debug("Field {}.{} is annotated with @CommandHandlingMemberCollection. "
                                         + "Checking {} for Command Handlers",
                                 targetType.getSimpleName(), field.getName(), entityType.getSimpleName()
                    );
                }
                return new EntityCollectionFieldAccessor(entityType, annotation, parent, field);
            }
			return null;
		}
    }
    
	private static Class<?> determineEntityType(Class<?> entityType, Field field, int genericTypeIndex) {
		if (AbstractAnnotatedEntity.class.equals(entityType)) {
		    final Type genericType = field.getGenericType();
		    if (genericType == null
		            || !(genericType instanceof ParameterizedType)
		            || ((ParameterizedType) genericType).getActualTypeArguments().length == 0) {
		        return null;
		    }
		    entityType = (Class<?>) ((ParameterizedType) genericType).getActualTypeArguments()[genericTypeIndex];
		}
		return entityType;
	}
 
	private static class EntityMapFieldAccessorFactory implements EntityFieldAccessorFactory {

		@Override
		public EntityAccessor getInstance(EntityAccessor parent, Field field) {
			if (field.isAnnotationPresent(CommandHandlingMemberMap.class)) {
				Class<?> targetType = parent.getEntityType();
                CommandHandlingMemberMap annotation = field.getAnnotation(CommandHandlingMemberMap.class);
                if (!Map.class.isAssignableFrom(field.getType())) {
                    throw new AxonConfigurationException(String.format(
                            "Field %s.%s is annotated with @CommandHandlingMemberMap, but the declared type of "
                                    + "the field is not assignable to java.util.Map.",
                            targetType.getSimpleName(), field.getName()));
                }
                Class<?> entityType = determineEntityType(annotation.entityType(), field, 1);
                if(entityType == null) {
                	throw new AxonConfigurationException(String.format(
    		                "Field %s.%s is annotated with @CommandHandlingMemberMap, but the entity"
    		                        + " type is not indicated on the annotation, "
    		                        + "nor can it be deduced from the generic parameters",
    		                targetType.getSimpleName(), field.getName()));
                }
                if (logger.isDebugEnabled()) {
                    logger.debug("Field {}.{} is annotated with @CommandHandlingMemberMap. "
                                         + "Checking {} for Command Handlers",
                                 targetType.getSimpleName(), field.getName(), entityType.getSimpleName()
                    );
                }
                return new EntityMapFieldAccessor(entityType, annotation, parent, field);
            }
			return null;
		}		
	}
	
	private static abstract class AbstractEntityAccessor implements EntityAccessor {
		
		private final Class<?> entityType;
		
		public AbstractEntityAccessor(Class<?> entityType) {
			this.entityType = entityType;
		}

		@Override
		public Class<?> getEntityType() {
			return entityType;
		}
		
	}
	
    private static class SimpleEntityFieldAccessor extends AbstractEntityAccessor {

        private final EntityAccessor entityAccessor;
        private final Field field;

        public SimpleEntityFieldAccessor(EntityAccessor parent, Field field) {
        	super(field.getType());
            this.entityAccessor = parent;
            this.field = field;
        }

        @Override
        public Object getInstance(Object aggregateRoot, CommandMessage<?> commandMessage)
                throws IllegalAccessException {
            Object entity = entityAccessor.getInstance(aggregateRoot, commandMessage);
            return entity != null ? ReflectionUtils.getFieldValue(field, entity) : null;
        }
    }

   
    private static abstract class MultipleEntityFieldAccessor<T> extends AbstractEntityAccessor {

        private final EntityAccessor entityAccessor;
        private final Field field;
		private String commandTargetProperty;
        

        @SuppressWarnings("unchecked")
        public MultipleEntityFieldAccessor(Class entityType, String commandTargetProperty,
        		EntityAccessor entityAccessor, Field field) {
            super(entityType);
            this.entityAccessor = entityAccessor;
            this.commandTargetProperty = commandTargetProperty;
            this.field = field;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Object getInstance(Object aggregateRoot, CommandMessage<?> command) throws IllegalAccessException {
            final Object parentEntity = entityAccessor.getInstance(aggregateRoot, command);
            if (parentEntity == null) {
                return null;
            }
            T entityCollection = (T) ReflectionUtils.getFieldValue(field, parentEntity);
            Property<Object> commandProperty = PropertyAccessStrategy.getProperty(command.getPayloadType(), commandTargetProperty);

            if (commandProperty == null) {
                // TODO: Log failure. It seems weird that the property is not present
                return null;
            }
            Object commandId = commandProperty.getValue(command.getPayload());
            if (commandId == null) {
                return null;
            }
            return getEntity(entityCollection, commandId);
        }

		protected abstract Object getEntity(T entities,	Object commandId);
    }
    
    private static class EntityCollectionFieldAccessor extends MultipleEntityFieldAccessor<Collection<?>> {
    	private final Property<Object> entityProperty;
    	
		@SuppressWarnings("unchecked")
		public EntityCollectionFieldAccessor(Class entityType, CommandHandlingMemberCollection annotation,
				EntityAccessor entityAccessor, Field field) {
			super(entityType, annotation.commandTargetProperty(), entityAccessor, field);
            this.entityProperty = PropertyAccessStrategy.getProperty(entityType, annotation.entityId());
		}
		
		protected Object getEntity(Collection<?> entities, Object commandId) {
			for (Object entity : entities) {
                Object entityId = entityProperty.getValue(entity);
                if (entityId != null && entityId.equals(commandId)) {
                    return entity;
                }
            }
            return null;
		}
    }
    
	private static class EntityMapFieldAccessor extends MultipleEntityFieldAccessor<Map<?,?>> {

		public EntityMapFieldAccessor(Class entityType, CommandHandlingMemberMap annotation,
				EntityAccessor entityAccessor, Field field) {
			super(entityType, annotation.commandTargetProperty(), entityAccessor, field);
		}

		@Override
		protected Object getEntity(Map<?,?> entities, Object commandId) {
			return entities.get(commandId);
		}
	}
}
