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

	static String weak(long version) {
		return "W/" + strong(version);
	}

	static String strong(long version) {
		return "\"rev-" + version + "\"";
	}

	/** For a strong comparison: a weak ETag never matches, so it ends in 412. */
	static long strongVersion(String ifMatch) {
		return ifMatch.strip().startsWith("W/") ? -1 : version(ifMatch);
	}

	/** An If-Match that is not an ETag of this API never matches, so it ends in 412. */
	static long version(String ifMatch) {
		Matcher matcher = ETAG.matcher(ifMatch.strip());
		return matcher.matches() ? Long.parseLong(matcher.group(1)) : -1;
	}

}
