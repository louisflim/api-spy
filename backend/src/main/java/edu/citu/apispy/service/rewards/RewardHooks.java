package edu.citu.apispy.service.rewards;

import java.util.UUID;

/**
 * Gameplay reward grants triggered by mission progress. Mission progress validates the room run
 * first; implementations apply the amounts and anti-farming caps. {@link #NOOP} keeps plain unit
 * tests of mission progress free of a database.
 */
public interface RewardHooks {

	/** Returns the EXP actually granted (0 when capped or the enemy type is unknown). */
	int enemyDefeated(String participantCode, String missionId, String rankCode, UUID roomGeneration, String enemyType);

	/** Boss EXP, granted once per mission. */
	void bossDefeated(String participantCode, String missionId, String rankCode, UUID roomGeneration);

	/** First-clear coins, granted once per mission (never for replays). */
	void firstClear(String participantCode, String missionId, String rankCode);

	RewardHooks NOOP = new RewardHooks() {
		@Override
		public int enemyDefeated(String p, String m, String r, UUID g, String t) {
			return 0;
		}

		@Override
		public void bossDefeated(String p, String m, String r, UUID g) {
		}

		@Override
		public void firstClear(String p, String m, String r) {
		}
	};
}
