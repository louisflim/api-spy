package edu.citu.apispy.auth;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class PasswordPolicy {

	private static final Pattern LETTER = Pattern.compile("[A-Za-z]");
	private static final Pattern NUMBER = Pattern.compile("[0-9]");
	private static final Pattern SPECIAL = Pattern.compile("[^A-Za-z0-9]");

	private PasswordPolicy() {
	}

	public static List<String> validate(String password, String confirmPassword) {
		List<String> errors = new ArrayList<>();
		if (password == null || password.length() < 8) {
			errors.add("Password must be at least 8 characters.");
		}
		if (password == null || !LETTER.matcher(password).find()) {
			errors.add("Password must contain at least one letter.");
		}
		if (password == null || !NUMBER.matcher(password).find()) {
			errors.add("Password must contain at least one number.");
		}
		if (password == null || !SPECIAL.matcher(password).find()) {
			errors.add("Password must contain at least one special character.");
		}
		if (password == null || confirmPassword == null || !password.equals(confirmPassword)) {
			errors.add("Confirm password must match password.");
		}
		return errors;
	}

	public static boolean isValid(String password, String confirmPassword) {
		return validate(password, confirmPassword).isEmpty();
	}
}
