public class MiniDnsResolver extends DNSResolver implements SmackInitializer {

    private static final MiniDnsResolver INSTANCE = new MiniDnsResolver();
    private static final ResolverApi DNSSEC_RESOLVER = DnssecResolverApi.INSTANCE;
    private static final ResolverApi NON_DNSSEC_RESOLVER = ResolverApi.INSTANCE;

    public static DNSResolver getInstance() { return INSTANCE; }

    public MiniDnsResolver() { super(true); }

    @Override
    protected Set<SRV> lookupSrvRecords0(DnsName name,
                                         List<RemoteConnectionEndpointLookupFailure> failures,
                                         DnssecMode dnssecMode) {
        ResolverApi resolver = getResolver(dnssecMode);
        try {
            SrvResolverResult res = resolver.resolveSrv(name);
            if (res.getResolutionUnsuccessfulException() != null) {
                addFailure(failures, name, res.getResolutionUnsuccessfulException());
                return null;
            }
            if (shouldAbortIfNotAuthentic(name, dnssecMode, res, failures)) return null;
            return res.getAnswers();
        } catch (IOException e) {
            addFailure(failures, name, e);
            return null;
        }
    }

    @Override
    protected List<InetAddress> lookupHostAddress0(DnsName name,
                                                   List<RemoteConnectionEndpointLookupFailure> failures,
                                                   DnssecMode dnssecMode) {
        ResolverApi resolver = getResolver(dnssecMode);
        ResolverResult<A> aRes;
        ResolverResult<AAAA> aaaaRes;
        try {
            aRes = resolver.resolve(name, A.class);
            aaaaRes = resolver.resolve(name, AAAA.class);
        } catch (IOException e) {
            addFailure(failures, name, e);
            return null;
        }

        if (!aRes.wasSuccessful() && !aaaaRes.wasSuccessful()) {
            addFailure(failures, name, getExceptionFrom(aRes));
            addFailure(failures, name, getExceptionFrom(aaaaRes));
            return null;
        }
        if (shouldAbortIfNotAuthentic(name, dnssecMode, aRes, failures)
                || shouldAbortIfNotAuthentic(name, dnssecMode, aaaaRes, failures)) {
            return null;
        }
        return buildInetList(name, aRes, aaaaRes);
    }

    public static void setup() {
        DNSUtil.setDNSResolver(getInstance());
    }

    @Override
    public List<Exception> initialize() {
        setup();
        MiniDnsDane.setup();
        return null;
    }

    /* ---------- helpers ---------- */

    private static ResolverApi getResolver(DnssecMode mode) {
        return mode == DnssecMode.disabled ? NON_DNSSEC_RESOLVER : DNSSEC_RESOLVER;
    }

    private static void addFailure(List<RemoteConnectionEndpointLookupFailure> failures,
                                   DnsName name,
                                   Exception e) {
        failures.add(new RemoteConnectionEndpointLookupFailure.DnsLookupFailure(name, e));
    }

    private static List<InetAddress> buildInetList(DnsName name,
                                                   ResolverResult<A> aRes,
                                                   ResolverResult<AAAA> aaaaRes) {
        List<InetAddress> list = new ArrayList<>();
        for (A a : aRes.wasSuccessful() ? aRes.getAnswers() : Collections.emptySet()) {
            try { list.add(InetAddress.getByAddress(a.getIp())); } catch (UnknownHostException ignore) { }
        }
        for (AAAA aaaa : aaaaRes.wasSuccessful() ? aaaaRes.getAnswers() : Collections.emptySet()) {
            try { list.add(InetAddress.getByAddress(name.ace, aaaa.getIp())); } catch (UnknownHostException ignore) { }
        }
        return list;
    }

    private static boolean shouldAbortIfNotAuthentic(DnsName name,
                                                     DnssecMode dnssecMode,
                                                     ResolverResult<?> result,
                                                     List<RemoteConnectionEndpointLookupFailure> failures) { 
        switch (dnssecMode) {
            case needsDnssec:
            case needsDnssecAndDane:
                DnssecResultNotAuthenticException ex = result.getDnssecResultNotAuthenticException();
                if (ex != null) {
                    addFailure(failures, name, ex);
                    return true;
                }
                break;
            case disabled:
                break;
            default:
                throw new IllegalStateException("Unknown DnssecMode: " + dnssecMode);
        }
        return false;
    }

    private static ResolutionUnsuccessfulException getExceptionFrom(ResolverResult<?> res) {
        return new ResolutionUnsuccessfulException(res.getQuestion(), res.getResponseCode());
    }
}
