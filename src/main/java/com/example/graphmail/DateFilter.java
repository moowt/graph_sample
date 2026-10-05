package com.example.graphmail;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** 受信日時または送信日時による絞り込み条件 (指定日時以降)。 */
public record DateFilter(Field field, Instant since) {

    /** 絞り込みに使う日時プロパティ。 */
    public enum Field {
        RECEIVED("received", "receivedDateTime"),
        SENT("sent", "sentDateTime");

        private final String key;
        private final String property;

        Field(String key, String property) {
            this.key = key;
            this.property = property;
        }

        /** Graph の Message のプロパティ名。 */
        public String property() {
            return property;
        }

        static Field of(String value) {
            for (Field f : values()) {
                if (f.key.equalsIgnoreCase(value)) {
                    return f;
                }
            }
            throw new IllegalArgumentException("--date-field は received か sent を指定してください: " + value);
        }
    }

    /** 現在から直近 days 日以内。 */
    public static DateFilter lastDays(Field field, int days) {
        Instant since = Instant.now().minus(Duration.ofDays(days)).truncatedTo(ChronoUnit.SECONDS);
        return new DateFilter(field, since);
    }

    /** $filter の式。日時は UTC の ISO 8601 で、引用符なしで書く。 */
    public String toODataFilter() {
        return field.property + " ge " + since;
    }
}
