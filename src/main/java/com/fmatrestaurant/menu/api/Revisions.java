package com.fmatrestaurant.menu.api;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ETags built from the version of a resource, and the version an If-Match refers to.
 */
final class Revisions {

	private static final Pattern ETAG = Pattern.compile("(?:W/)?\"rev-(\\d+)\"");

	private Revisions() {
	}

	static String strong(long version) {
		return "\"rev-" + version + "\"";
	}

	/** For a strong comparison: a weak ETag never matches, so it ends in 412. */
	static long strongVersion(String ifMatch) {
		return ifMatch.strip().startsWith("W/") ? -1 : version(ifMatch);
	}

	/** An If-Match that is not an ETag of this API, or whose revision is out of range, never matches: 412. */
	private static long version(String ifMatch) {
		Matcher matcher = ETAG.matcher(ifMatch.strip());
		if (!matcher.matches()) {
			return -1;
		}
		try {
			return Long.parseLong(matcher.group(1));
		} catch (NumberFormatException _) {
			return -1;
		}
	}

}
