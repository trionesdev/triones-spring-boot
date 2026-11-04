package com.trionesdev.spring.boot.opentelemetry.util;

import com.trionesdev.commons.context.actor.Actor;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.BaggageEntry;
import org.apache.commons.collections4.MapUtils;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class OpenTelemetryUtils {
    public OpenTelemetryUtils() {
    }

    public static void baggage(Actor actor) {
        if (!Objects.isNull(actor)) {
            Baggage.current().toBuilder().put("X-Request-ActorId", actor.getActorId()).put("X-Request-Role", actor.getRole()).put("X-Request-UserId", actor.getUserId()).put("X-Request-TenantId", actor.getTenantId()).put("X-Request-Tenant-MemberId", actor.getTenantMemberId()).put("X-Request-Time", String.valueOf(Optional.ofNullable(actor.getTime()).map(Instant::toEpochMilli).orElse(Instant.now().toEpochMilli()))).build().makeCurrent();
        }
    }

    public static Actor actor() {
        Map<String, BaggageEntry> baggageEntryMap = Baggage.current().asMap();
        if (MapUtils.isEmpty(baggageEntryMap)) {
            return null;
        } else {
            Actor actor = new Actor();
            actor.setActorId(Optional.ofNullable(baggageEntryMap.get("X-Request-ActorId")).map(BaggageEntry::getValue).orElse(null));
            actor.setRole(Optional.ofNullable(baggageEntryMap.get("X-Request-Role")).map(BaggageEntry::getValue).orElse(null));
            actor.setUserId(Optional.ofNullable(baggageEntryMap.get("X-Request-UserId")).map(BaggageEntry::getValue).orElse(null));
            actor.setTenantId(Optional.ofNullable(baggageEntryMap.get("X-Request-TenantId")).map(BaggageEntry::getValue).orElse(null));
            actor.setTenantMemberId(Optional.ofNullable(baggageEntryMap.get("X-Request-Tenant-MemberId")).map(BaggageEntry::getValue).orElse(null));
            actor.setTime(Optional.ofNullable(baggageEntryMap.get("X-Request-Time")).map((t) -> {
                return Instant.ofEpochMilli(Long.parseLong(t.getValue()));
            }).orElse(Instant.now()));
            return actor;
        }
    }
}
