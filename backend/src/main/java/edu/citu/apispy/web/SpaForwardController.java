package edu.citu.apispy.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SPA deep-link fallback: non-API browser routes serve {@code index.html}
 * so React Router can resolve {@code /settings}, {@code /missions/...}, etc.
 *
 * <p>Place the Vite production build under {@code classpath:/static}
 * (typically {@code backend/src/main/resources/static}).
 * Static files that exist on disk are still served by Spring's resource handlers;
 * this controller only handles unmatched HTML navigations.
 */
@Controller
public class SpaForwardController {

	@GetMapping(value = {
			"/",
			"/login",
			"/register",
			"/forgot-password",
			"/dashboard",
			"/operations",
			"/training-academy",
			"/profile",
			"/loadout",
			"/settings",
			"/missions/**",
			"/app",
			"/app/**"
	})
	public String forwardSpa() {
		return "forward:/index.html";
	}
}
