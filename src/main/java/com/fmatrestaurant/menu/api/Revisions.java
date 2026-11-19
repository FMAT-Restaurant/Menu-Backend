package com.fmatrestaurant.menu.api;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ETags built from the version of a resource, and the version an If-Match refers to. Entries and offers add a
 * fingerprint of their derived availability ({@code "rev-<version>-<fingerprint>"}): it changes without a new
 * version, so a conditional GET never answers 304 with a stale {@code isAvailable}. An If-Match is compared only
 * by version, so a change of availability never makes the ETag of an administrator stale.
 */
public final class Revisions {

	private static final Pattern ETAG = Pattern.compile("(?:W/)?\"rev-(\\d+)(?:-[0-9a-z]+)?\"");

	private Revisions() {
	}

	public static String strong(long version) {
		return "\"rev-" + version + "\"";
	}

	/** A strong ETag that also changes with the fingerprint of what the version does not cover. */
	public static String strong(long version, String fingerprint) {
		return "\"rev-" + version + "-" + fingerprint + "\"";
	}

	/** For a strong comparison: a weak ETag never matches, so it ends in 412. */
	public static long strongVersion(String ifMatch) {
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
