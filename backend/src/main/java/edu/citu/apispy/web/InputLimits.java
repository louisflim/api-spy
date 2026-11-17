package edu.citu.apispy.web;

/**
 * Shared upper bounds for user-supplied strings (aligned with DB columns where applicable).
 */
public final class InputLimits {

	public static final int USERNAME = 64;
	public static final int EMAIL = 320;
	public static final int PASSWORD = 128;
	public static final int RESET_TOKEN = 128;

	public static final int ID = 64;
	public static final int CONCEPT_TAG = 128;
	public static final int HTTP_METHOD = 16;
	public static final int PATH = 512;
	public static final int BODY = 16_384;
	public static final int ROOM_GENERATION = 36;
	public static final int RESET_REASON = 16;

	public static final int HEADER_ENTRIES = 32;
	public static final int HEADER_NAME = 128;
	public static final int HEADER_VALUE = 2_048;

	/** Max JSON / API request body size (256 KiB). */
	public static final int MAX_REQUEST_BYTES = 256 * 1024;

	private InputLimits() {
	}
}
