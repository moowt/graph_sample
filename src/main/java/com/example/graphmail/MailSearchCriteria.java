package com.example.graphmail;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * メール一覧の検索条件。{@link #builder()} で組み立てる。
 *
 * <pre>
 * MailSearchCriteria criteria = MailSearchCriteria.builder()
 *         .folder("inbox")
 *         .withinLast(Duration.ofDays(7))
 *         .sortOrder(SortOrder.NEWEST_FIRST)
 *         .top(20)
 *         .build();
 * </pre>
 *
 * <p>日時の条件と並び順は、メールの日時として {@value #DATE_PROPERTY} を使う。
 * Exchange はこの値を、受信メールでは受信日時、送信済みメールでは送信日時 (送信済みアイテムに入った日時) に設定するため、
 * 受信・送信を区別せずに 1 つの日時として扱える。
 */
public final class MailSearchCriteria {

    /** 日時の条件と並び順に使う Message のプロパティ。 */
    public static final String DATE_PROPERTY = "receivedDateTime";

    /** 並び順。 */
    public enum SortOrder {
        /** 日時の新しい順。 */
        NEWEST_FIRST("desc"),
        /** 日時の古い順。 */
        OLDEST_FIRST("asc");

        private final String direction;

        SortOrder(String direction) {
            this.direction = direction;
        }
    }

    private final String folder;
    private final Instant since;
    private final Instant until;
    private final SortOrder sortOrder;
    private final int top;

    private MailSearchCriteria(Builder b) {
        this.folder = b.folder;
        this.since = b.since;
        this.until = b.until;
        this.sortOrder = b.sortOrder;
        this.top = b.top;
    }

    /**
     * 既定値 (inbox・条件なし・新しい順・10 件) で初期化したビルダーを返す。
     *
     * @return ビルダー
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 対象フォルダーを返す。
     *
     * @return inbox, sentitems などのよく知られた名前、またはフォルダー ID
     */
    public String folder() {
        return folder;
    }

    /**
     * 取得件数を返す。
     *
     * @return 取得件数 (1〜100)
     */
    public int top() {
        return top;
    }

    /**
     * 日時の条件を $filter の式に変換する。
     *
     * @return $filter の式 (例: {@code receivedDateTime ge 2026-10-01T00:00:00Z})。条件が無ければ null
     */
    public String toODataFilter() {
        List<String> conditions = new ArrayList<>();
        if (since != null) {
            conditions.add(DATE_PROPERTY + " ge " + since);
        }
        if (until != null) {
            conditions.add(DATE_PROPERTY + " lt " + until);
        }
        return conditions.isEmpty() ? null : String.join(" and ", conditions);
    }

    /**
     * 並び順を $orderby の式に変換する。
     * $filter と併用する場合、$orderby のプロパティは $filter にも含まれている必要がある
     * (異なると Graph が 400 InefficientFilter を返す)。日時の条件と同じプロパティを使うことで満たしている。
     *
     * @return $orderby の式 (例: {@code receivedDateTime desc})
     */
    public String toODataOrderBy() {
        return DATE_PROPERTY + " " + sortOrder.direction;
    }

    /**
     * ログ・確認用に、Graph に渡すクエリの形で条件を表した文字列を返す。
     *
     * @return 条件の文字列表現
     */
    @Override
    public String toString() {
        return "folder=" + folder + ", $filter=" + toODataFilter() + ", $orderby=" + toODataOrderBy() + ", $top=" + top;
    }

    /** {@link MailSearchCriteria} のビルダー。未指定の項目は既定値になる。 */
    public static final class Builder {
        private String folder = "inbox";
        private Instant since;
        private Instant until;
        private SortOrder sortOrder = SortOrder.NEWEST_FIRST;
        private int top = 10;

        private Builder() {
        }

        /**
         * 対象フォルダーを指定する。既定は inbox。
         *
         * @param folder inbox, sentitems などのよく知られた名前、またはフォルダー ID
         * @return このビルダー
         */
        public Builder folder(String folder) {
            this.folder = folder;
            return this;
        }

        /**
         * この日時以降のメールに絞り込む (指定日時を含む)。
         *
         * @param since 開始日時。null なら下限なし
         * @return このビルダー
         */
        public Builder since(Instant since) {
            this.since = since;
            return this;
        }

        /**
         * この日時より前のメールに絞り込む (指定日時を含まない)。
         *
         * @param until 終了日時。null なら上限なし
         * @return このビルダー
         */
        public Builder until(Instant until) {
            this.until = until;
            return this;
        }

        /**
         * 現在から指定期間以内のメールに絞り込む。{@link #since(Instant)} の値を上書きする。
         *
         * @param period 期間 (例: {@code Duration.ofDays(7)})
         * @return このビルダー
         */
        public Builder withinLast(Duration period) {
            this.since = Instant.now().minus(period).truncatedTo(ChronoUnit.SECONDS);
            return this;
        }

        /**
         * 並び順を指定する。既定は新しい順。
         *
         * @param sortOrder 並び順
         * @return このビルダー
         */
        public Builder sortOrder(SortOrder sortOrder) {
            this.sortOrder = sortOrder;
            return this;
        }

        /**
         * 取得件数を指定する。既定は 10。
         *
         * @param top 取得件数 (1〜100)
         * @return このビルダー
         */
        public Builder top(int top) {
            this.top = top;
            return this;
        }

        /**
         * 指定内容を検証して検索条件を生成する。
         *
         * @return 検索条件
         * @throws IllegalArgumentException folder / sortOrder が未指定、top が範囲外、
         *                                  または since が until 以降の場合
         */
        public MailSearchCriteria build() {
            if (folder == null || folder.isBlank()) {
                throw new IllegalArgumentException("folder を指定してください。");
            }
            if (sortOrder == null) {
                throw new IllegalArgumentException("sortOrder を指定してください。");
            }
            if (top < 1 || top > 100) {
                throw new IllegalArgumentException("top は 1〜100 で指定してください: " + top);
            }
            if (since != null && until != null && !since.isBefore(until)) {
                throw new IllegalArgumentException("since は until より前の日時を指定してください: " + since + " / " + until);
            }
            return new MailSearchCriteria(this);
        }
    }
}
