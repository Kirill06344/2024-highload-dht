package ru.vk.itmo.test.bazhenovkirill;

import one.nio.http.*;
import one.nio.server.AcceptorConfig;
import ru.vk.itmo.ServiceConfig;
import ru.vk.itmo.dao.BaseEntry;
import ru.vk.itmo.dao.Config;
import ru.vk.itmo.dao.Entry;
import ru.vk.itmo.test.reference.dao.DaoImpl;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;

public class ServerImpl extends HttpServer {

    private final DaoImpl dao;

    public ServerImpl(ServiceConfig config) throws IOException {
        super(createServerConfig(config));
        dao = new DaoImpl(new Config(config.workingDir(), 666));

    }

    private static HttpServerConfig createServerConfig(ServiceConfig config) {
        HttpServerConfig serverConfig = new HttpServerConfig();
        AcceptorConfig acceptorConfig = new AcceptorConfig();
        acceptorConfig.port = config.selfPort();
        acceptorConfig.reusePort = true;

        serverConfig.acceptors = new AcceptorConfig[]{acceptorConfig};
        serverConfig.closeSessions = true;
        return serverConfig;
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_GET)
    public Response getEntityById(@Param(value = "id", required = true) String id) {
        if (isInvalidId(id)) {
            return new Response(Response.BAD_REQUEST, new byte[]{});
        }

        MemorySegment key = convertStringToMemorySegment(id);
        Entry<MemorySegment> entry = dao.get(key);

        if (entry == null) {
            return new Response(Response.NOT_FOUND, new byte[]{});
        }

        return Response.ok(entry.value().toArray(ValueLayout.JAVA_BYTE));
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_DELETE)
    public Response deleteEntityById(@Param(value = "id", required = true) String id) {
        if (isInvalidId(id)) {
            return new Response(Response.BAD_REQUEST, new byte[]{});
        }

        MemorySegment key = convertStringToMemorySegment(id);
        dao.upsert(new BaseEntry<>(key, null));

        return new Response(Response.ACCEPTED, new byte[]{});
    }

    @Path("/v0/entity")
    public Response respondMethodNotAllowed() {
        return new Response(Response.METHOD_NOT_ALLOWED, new byte[]{});
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_PUT)
    public Response putEntity(@Param(value = "id", required = true) String id, Request request) {
        byte[] body = request.getBody();
        if (isInvalidId(id) || body == null) {
            return new Response(Response.BAD_REQUEST, new byte[]{});
        }

        MemorySegment key = convertStringToMemorySegment(id);
        MemorySegment value = MemorySegment.ofArray(request.getBody());
        dao.upsert(new BaseEntry<>(key, value));

        return new Response(Response.CREATED, new byte[]{});
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        Response response = new Response(Response.BAD_REQUEST, Response.EMPTY);
        session.sendResponse(response);
    }

    @Override
    public synchronized void stop() {
        try {
            dao.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        super.stop();
    }

    private boolean isInvalidId (String id) {
        return id == null || id.isEmpty();
    }

    private MemorySegment convertStringToMemorySegment(String id) {
        return MemorySegment.ofArray(id.getBytes(StandardCharsets.UTF_8));
    }
}
