import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class SmackInitialization {

    static final String SMACK_VERSION;
    private static final String DEFAULT_CONFIG_FILE = "org.jivesoftware.smack/smack-config.xml";
    private static final Logger LOGGER = Logger.getLogger(SmackInitialization.class.getName());

    static {
        SMACK_VERSION = resolveVersion();
        configureDisabledClasses();
        loadConfigFile();
        registerCoreModules();
        enableDebugIfRequested();
        SmackConfiguration.smackInitialized = true;
    }

    /* ------------- API ------------- */

    public static void processConfigFile(InputStream cfg, Collection<Exception> ex) throws Exception {
        processConfigFile(cfg, ex, SmackInitialization.class.getClassLoader());
    }

    public static void processConfigFile(InputStream cfg, Collection<Exception> ex, ClassLoader cl) throws Exception {
        XmlPullParser parser = PacketParserUtils.getParserFor(cfg);
        XmlPullParser.Event event;
        do {
            event = parser.getEventType();
            if (event == XmlPullParser.Event.START_ELEMENT) {
                if ("startupClasses".equals(parser.getName())) {
                    parseInicialization(parser, false, ex, cl);                           // ← único Long Parameter List
                } else if ("optionalStartupClasses".equals(parser.getName())) {
                    parseInicialization(parser, true, ex, cl);
                }
            }
            event = parser.next();
        } while (event != XmlPullParser.Event.END_DOCUMENT);
        CloseableUtil.maybeClose(cfg, LOGGER);
    }

    /* ------------- helpers ------------- */

    private static String resolveVersion() {
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(FileUtils.getStreamForClasspathFile("org.jivesoftware.smack/version", null),
                                      StandardCharsets.UTF_8))) {
            return r.readLine();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Could not determine Smack version", e);
            return "unknown";
        }
    }

    private static void configureDisabledClasses() {
        String disabled = System.getProperty("smack.disabledClasses");
        if (disabled != null) Collections.addAll(SmackConfiguration.disabledSmackClasses, disabled.split(","));
    }

    private static void loadConfigFile() {
        try (InputStream in = FileUtils.getStreamForClasspathFile(DEFAULT_CONFIG_FILE, null)) {
            processConfigFile(in, null);
        } catch (Exception e) {
            throw new IllegalStateException("Could not load/parse Smack configuration file", e);
        }
    }

    private static void registerCoreModules() {
        SmackConfiguration.addCompressionHandler(new Java7ZlibInputOutputStream());
        XmppCompressionManager.registerXmppCompressionFactory(ZlibXmppCompressionFactory.INSTANCE);
        SASLAuthentication.registerSASLMechanism(new SCRAMSHA1Mechanism());
        SASLAuthentication.registerSASLMechanism(new ScramSha1PlusMechanism());
        SASLAuthentication.registerSASLMechanism(new SASLXOauth2Mechanism());
        SASLAuthentication.registerSASLMechanism(new SASLAnonymous());
        ProviderManager.addIQProvider(Bind.ELEMENT, Bind.NAMESPACE, new BindIQProvider());
        ProviderManager.addExtensionProvider(Message.Body.ELEMENT, Message.Body.NAMESPACE, new BodyElementProvider());
        ProviderManager.addExtensionProvider(Message.Thread.ELEMENT, Message.Thread.NAMESPACE, new MessageThreadElementProvider());
        ProviderManager.addExtensionProvider(Message.Subject.ELEMENT, Message.Subject.NAMESPACE, new MessageSubjectElementProvider());
        ProviderManager.addNonzaProvider(SaslChallengeProvider.INSTANCE);
        ProviderManager.addNonzaProvider(SaslSuccessProvider.INSTANCE);
        ProviderManager.addNonzaProvider(SaslFailureProvider.INSTANCE);
        ProviderManager.addNonzaProvider(TlsProceedProvider.INSTANCE);
        ProviderManager.addNonzaProvider(TlsFailureProvider.INSTANCE);
        ProviderManager.addNonzaProvider(CompressedProvider.INSTANCE);
        ProviderManager.addNonzaProvider(FailureProvider.INSTANCE);
        SmackConfiguration.addModule(Bind2ModuleDescriptor.class);
        SmackConfiguration.addModule(CompressionModuleDescriptor.class);
        SmackConfiguration.addModule(InstantStreamResumptionModuleDescriptor.class);
    }

    private static void enableDebugIfRequested() {
        try {
            if (Boolean.getBoolean("smack.debugEnabled")) SmackConfiguration.DEBUG = true;
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Could not handle debugEnable property", e);
        }
    }

  

    private static void parseInicialization(XmlPullParser parser,
                                            boolean optional,
                                            Collection<Exception> exceptions,
                                            ClassLoader cl) throws Exception {         
        String start = parser.getName();
        XmlPullParser.Event event;
        do {
            event = parser.next();
            if (event == XmlPullParser.Event.START_ELEMENT && "className".equals(parser.getName())) {
                String className = parser.nextText();
                if (SmackConfiguration.isDisabledSmackClass(className)) continue;
                try {
                    loadSmackClass(className, optional, cl);
                } catch (Exception e) {
                    if (exceptions != null) exceptions.add(e); else throw e;
                }
            }
        } while (!(event == XmlPullParser.Event.END_ELEMENT && start.equals(parser.getName())));
    }

    public static void loadRequiredSmackClass(String className, ClassLoader classLoader)
        throws ClassNotFoundException,
               NoSuchMethodException,
               InvocationTargetException,
               InstantiationException,
               IllegalAccessException {
    SmackInitializer initializer = instantiateInitializer(className, classLoader);
    if (initializer != null) {
        logInitializationExceptions(initializer.initialize());
    }
}

    public static void loadOptionalSmackClass(String className, ClassLoader classLoader) {
        try {
            loadRequiredSmackClass(className, classLoader);
        } catch (ClassNotFoundException e) {
            LOGGER.log(Level.FINE, "Optional startup class {0} not found", className);
        } catch (ReflectiveOperationException e) {
            // Se ocorrer qualquer erro de reflexão, registramos mas não interrompemos
            LOGGER.log(Level.WARNING, "Error initializing optional class " + className, e);
        }
    }

    private static SmackInitializer instantiateInitializer(String className, ClassLoader classLoader)
            throws ClassNotFoundException,
                NoSuchMethodException,
                InvocationTargetException,
                InstantiationException,
                IllegalAccessException {
        Class<?> clazz = Class.forName(className, true, classLoader);
        if (!SmackInitializer.class.isAssignableFrom(clazz)) {
            return null;  // Não implementa a interface, nada a fazer
        }
        // Assumimos construtor público sem argumentos
        return (SmackInitializer) clazz.getDeclaredConstructor().newInstance();
    }

    private static void logInitializationExceptions(List<Exception> exceptions) {
        if (exceptions != null) {
            exceptions.forEach(e -> LOGGER.log(Level.SEVERE, "Initializer exception", e));
        }
    }

}
