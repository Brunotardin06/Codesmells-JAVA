/** 
 * The library for metadata about events (and their fields).
 */
public class EventLibrary extends ModuleClassLibrary<Event> {
  private static final Logger logger=LoggerFactory.getLogger(EventLibrary.class);
  public EventLibrary(  ModuleEnvironment environment,  ReflectFactory reflectFactory,  CopyStrategyLibrary copyStrategyLibrary){
    super(environment,reflectFactory,copyStrategyLibrary);
  }
   protected <C extends Event>ClassMetadata<C,?> createMetadata(  Class<C> type,  ReflectFactory factory,  CopyStrategyLibrary copyStrategies,  ResourceUrn uri){
    try {
      return new EventMetadata<>(type,copyStrategies,factory,uri);
    }
 catch (    NoSuchMethodException e) {
      logger.error("Unable to register class {}: Default Constructor Required",type.getSimpleName(),e);
      return null;
    }
  }
   @SuppressWarnings("unchecked") public <T extends Event>EventMetadata<T> getMetadata(  Class<T> clazz){
    return (EventMetadata<T>)super.getMetadata(clazz);
  }
   @SuppressWarnings("unchecked") public <T extends Event>EventMetadata<T> getMetadata(  T object){
    return (EventMetadata<T>)super.getMetadata(object);
  }
   @SuppressWarnings("unchecked") public EventMetadata<? extends Event> getMetadata(  ResourceUrn uri){
    return (EventMetadata<? extends Event>)super.getMetadata(uri);
  }
}
