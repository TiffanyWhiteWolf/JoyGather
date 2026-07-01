package com.quju.service;

import com.quju.dto.CommonDtos;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class IntegrationServiceGeocodingTest extends TestBase {
    private HttpServer server;
    private IntegrationService integrationService;

    @BeforeEach
    void startMapServer() throws IOException {
        jdbc.execute("create table third_party_events (id varchar(64) primary key, provider varchar(40), operation varchar(80), status varchar(32), request_summary varchar(1000), response_summary varchar(1000), error varchar(1000), duration_ms int)");
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/search", exchange -> respond(exchange,
                "[{\"name\":\"北京航空航天大学\",\"display_name\":\"北京航空航天大学, 37, 学院路, 花园路街道, 海淀区, 北京市, 100083, 中国\",\"lat\":\"39.9809427\",\"lon\":\"116.3409787\",\"address\":{\"amenity\":\"北京航空航天大学\",\"road\":\"学院路\",\"city\":\"海淀区\",\"ISO3166-2-lvl4\":\"CN-BJ\"}}]"));
        server.createContext("/reverse", exchange -> respond(exchange,
                "{\"name\":\"学院路\",\"display_name\":\"学院路, 花园路街道, 海淀区, 北京市, 100083, 中国\",\"lat\":\"39.9809427\",\"lon\":\"116.3409787\",\"address\":{\"road\":\"学院路\",\"suburb\":\"花园路街道\",\"city\":\"海淀区\",\"ISO3166-2-lvl4\":\"CN-BJ\"}}"));
        server.start();

        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSender> mailProvider = mock(ObjectProvider.class);
        integrationService = new IntegrationService(jdbc, mailProvider);
        ReflectionTestUtils.setField(integrationService, "amapKey", "");
        ReflectionTestUtils.setField(integrationService, "nominatimBaseUrl", "http://127.0.0.1:" + server.getAddress().getPort());
        ReflectionTestUtils.setField(integrationService, "nominatimUserAgent", "JoyGather-Test/1.0");
        ReflectionTestUtils.setField(integrationService, "nominatimMinIntervalMs", 0L);
        ReflectionTestUtils.setField(integrationService, "nominatimCacheTtlMs", 60000L);
        ReflectionTestUtils.setField(integrationService, "publicBaseUrl", "http://localhost:5173");
    }

    @AfterEach
    void stopMapServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void searchReturnsRealProviderCoordinatesInsteadOfRecommendationPlaceholder() {
        List<CommonDtos.GeoPoint> points = integrationService.searchAmap("北京航空航天大学", "北京");

        assertEquals(1, points.size());
        assertEquals("北京航空航天大学", points.get(0).getName());
        assertEquals("海淀区", points.get(0).getDistrict());
        assertEquals(new BigDecimal("116.3409787"), points.get(0).getLongitude());
        assertEquals(new BigDecimal("39.9809427"), points.get(0).getLatitude());
    }

    @Test
    void reverseGeocodingReturnsReadablePlaceNameAndAddress() {
        CommonDtos.GeoPoint point = integrationService.reverseGeocode(
                new BigDecimal("116.3409787"), new BigDecimal("39.9809427"));

        assertEquals("学院路", point.getName());
        assertEquals("海淀区", point.getDistrict());
        assertEquals("北京", point.getCity());
        assertEquals("学院路, 花园路街道, 海淀区, 北京市, 100083, 中国", point.getAddress());
    }

    private void respond(HttpExchange exchange, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
