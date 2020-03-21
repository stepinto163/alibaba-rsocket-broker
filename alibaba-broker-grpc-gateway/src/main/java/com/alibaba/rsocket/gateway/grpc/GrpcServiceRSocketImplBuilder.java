package com.alibaba.rsocket.gateway.grpc;

import com.alibaba.rsocket.ServiceLocator;
import com.alibaba.rsocket.upstream.UpstreamCluster;
import com.alibaba.rsocket.upstream.UpstreamManager;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;
import org.lognet.springboot.grpc.GRpcService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * gRPC service RSocket implementation build
 *
 * @author leijuan
 */
public class GrpcServiceRSocketImplBuilder<T> {
    private Class<?> serviceStub;
    private GrpcReactiveCallInterceptor interceptor = new GrpcReactiveCallInterceptor();

    public static <T> GrpcServiceRSocketImplBuilder<T> stub(Class<T> serviceStub, String serviceName) {
        GrpcServiceRSocketImplBuilder<T> builder = new GrpcServiceRSocketImplBuilder<T>();
        builder.serviceStub = serviceStub;
        builder.interceptor.setService(serviceName);
        return builder;
    }

    public GrpcServiceRSocketImplBuilder<T> upstreamManager(UpstreamManager upstreamManager) {
        String serviceId = ServiceLocator.serviceId(interceptor.getGroup(), interceptor.getService(), interceptor.getVersion());
        UpstreamCluster upstream = upstreamManager.findClusterByServiceId(serviceId);
        if (upstream == null) {
            upstream = upstreamManager.findBroker();
        }
        interceptor.setRsocket(upstream.getLoadBalancedRSocket());
        return this;
    }

    public GrpcServiceRSocketImplBuilder<T> group(String group) {
        interceptor.setGroup(group);
        return this;
    }


    public GrpcServiceRSocketImplBuilder<T> version(String version) {
        interceptor.setVersion(version);
        return this;
    }

    /**
     * timeout configuration, and default timeout is 3000 millis
     * if the call is long time task, please set it to big value
     *
     * @param millis millis
     * @return builder
     */
    public GrpcServiceRSocketImplBuilder<T> timeoutMillis(int millis) {
        interceptor.setTimeout(Duration.ofMillis(millis));
        return this;
    }

    public T build() throws Exception {
        Class<T> dynamicType = (Class<T>) new ByteBuddy()
                .subclass(serviceStub)
                .name(serviceStub.getSimpleName() + "Impl")
                .annotateType(AnnotationDescription.Builder.ofType(GRpcService.class).build())
                .method(ElementMatchers.returns(target -> target.isAssignableFrom(Mono.class) || target.isAssignableFrom(Flux.class)))
                .intercept(MethodDelegation.to(interceptor))
                .make()
                .load(getClass().getClassLoader())
                .getLoaded();
        return dynamicType.newInstance();
    }

}
