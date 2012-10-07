package net.mikaboshi.intra_mart.tools.log_stats.report;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import net.mikaboshi.intra_mart.tools.log_stats.entity.ExceptionLog;
import net.mikaboshi.intra_mart.tools.log_stats.entity.Log;
import net.mikaboshi.intra_mart.tools.log_stats.entity.RequestLog;
import net.mikaboshi.intra_mart.tools.log_stats.entity.TransitionLog;
import net.mikaboshi.intra_mart.tools.log_stats.entity.TransitionType;
import net.mikaboshi.intra_mart.tools.log_stats.util.LongListFactory;
import net.mikaboshi.intra_mart.tools.log_stats.util.SingleIntFactory;

import org.apache.commons.collections.map.LazyMap;
import org.apache.commons.lang.StringUtils;

/**
 * 全体の統計情報
 */
public class GrossStatistics {

	/**
	 * リクエストURL(im_action付き） => 処理時間
	 */
	@SuppressWarnings("unchecked")
	private Map<String, List<Long>> urlPageTimesMap = LazyMap.decorate(
			new HashMap<String, List<Long>>(),
			LongListFactory.INSTANCE);

	/**
	 * 例外ログ => 件数
	 */
	@SuppressWarnings("unchecked")
	private Map<ExceptionLog, int[]> exceptionCountMap = LazyMap.decorate(
			new HashMap<ExceptionLog, int[]>(),
			SingleIntFactory.INSTANCE);

	/**
	 * 処理時間順のリクエストランク
	 */
	private final RequestEntry[] requestPageTimeRank;

	/**
	 * リクエスト数合計
	 */
	private int requestCount = 0;

	/**
	 * セッション情報
	 */
	private SessionMap sessionMap = new SessionMap();

	/**
	 * 画面遷移ログのみ（リクエストログなし）ならばtrue
	 */
	private final boolean transitionLogOnly;

	/**
	 * コンストラクタ
	 * @param requestPageTimeRankSize　処理時間順のリクエストランクサイズ
	 * @param transitionLogOnly 画面遷移ログのみ（リクエストログなし）かどうか
	 */
	public GrossStatistics(int requestPageTimeRankSize, boolean transitionLogOnly) {
		this.requestPageTimeRank = new RequestEntry[requestPageTimeRankSize];
		this.transitionLogOnly = transitionLogOnly;
	}

	/**
	 * リクエストログを追加する。
	 * @param log
	 */
	public void add(RequestLog log) {

		if (log == null) {
			return;
		}

		addRequestInfo(log, log.getRequestUrlWithImAction(), log.requestPageTime);


	}

	/**
	 * 画面遷移ログを追加する。
	 * @param log
	 */
	public void add(TransitionLog log) {

		if (log == null) {
			return;
		}

		this.sessionMap.putUserId(log.clientSessionId, log.transitionAccessUserId);

		if (this.transitionLogOnly && log.type == TransitionType.REQUEST) {
			// 画面遷移ログ（type=REQUEST）からリクエスト統計を構築する

			addRequestInfo(log, log.transitionPathPageNext, log.transitionTimeResponse);
		}

		registerSessionAccess(log);
	}

	/**
	 * 例外ログを追加する。
	 * @param log
	 */
	public void add(ExceptionLog log) {

		if (log == null) {
			return;
		}

		if (StringUtils.isNotEmpty(log.stackTrace)) {
			this.exceptionCountMap.get(log)[0]++;
		}
	}

	/**
	 * リクエスト数合計を取得する。
	 * @return
	 */
	public int getRequestCount() {
		return requestCount;
	}

	/**
	 * 例外件数マップを取得する。
	 * @return
	 */
	public Map<ExceptionLog, int[]> getExceptionCountMap() {
		return exceptionCountMap;
	}

	/**
	 * リクエストURL(im_action付き） => 処理時間マップを取得する。
	 * @return
	 */
	public Map<String, List<Long>> getUrlPageTimesMap() {
		return urlPageTimesMap;
	}

	/**
	 * 処理時間順のリクエストランクを取得する。
	 * @return
	 */
	public RequestEntry[] getRequestPageTimeRank() {
		return requestPageTimeRank;
	}

	/**
	 * セッション情報を取得する。
	 * @return
	 */
	public SessionMap getSessionMap() {
		return sessionMap;
	}

	public static class RequestEntry {
		public String url  = null;
		public long time = 0L;
		public Date date = null;
		public String sessionId = null;
	}

	private void addRequestInfo(Log log, String url, long responseTime) {

		this.requestCount++;

		if (StringUtils.isNotEmpty(url)) {
			this.urlPageTimesMap.get(url).add(responseTime);
		}

		this.sessionMap.addPageTime(log.clientSessionId, responseTime);

		registerSessionAccess(log);


		// 処理時間ランクを設定
		int size = this.requestPageTimeRank.length;

		if (this.requestPageTimeRank[size - 1] == null ||
				responseTime > this.requestPageTimeRank[size - 1].time) {

			// ランクの入れ替え
			for (int i = 0; i < size; i++) {

				if (this.requestPageTimeRank[i] != null &&
						responseTime <= this.requestPageTimeRank[i].time) {
					continue;
				}

				// シフト
				for (int j = size - 1; j >= i + 1; j--) {
					this.requestPageTimeRank[j] = this.requestPageTimeRank[j - 1];
				}

				RequestEntry requestEntry = new RequestEntry();
				this.requestPageTimeRank[i] = requestEntry;

				requestEntry.url = url;
				requestEntry.time = responseTime;
				requestEntry.date = log.date;
				requestEntry.sessionId = log.clientSessionId;

				break;
			}
		}
	}

	/**
	 * セッションアクセス履歴を登録する。
	 * @param log
	 */
	private void registerSessionAccess(Log log) {

		this.sessionMap.putLastAccessDate(log.clientSessionId, log.date);
		this.sessionMap.putFirstAccessDate(log.clientSessionId, log.date);

		String url = log.getUrl();

		if (url != null &&
				(url.endsWith("/user.logout") || url.endsWith(".portal"))) {

			this.sessionMap.putLogoutDate(log.clientSessionId, log.date);
		}
	}
}
