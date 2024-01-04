/*
 * Tigase MIX - MIX component for Tigase
 * Copyright (C) 2020 Tigase, Inc. (office@tigase.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 */
package tigase.mix.model;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum JIDVisibility {
	// values taken from https://xmpp.org/extensions/attic/xep-0369-0.6.html
	visible("jid-mandatory-visible","JID Visible"),
	maybeVisible("jid-optionally-visible","JID Maybe Visible"),
	hidden("jid-hidden", "JID Hidden");

	public static JIDVisibility[] ALL_VALUES = EnumSet.allOf(JIDVisibility.class).toArray(JIDVisibility[]::new);
	private static Map<String, JIDVisibility> ALL_VALUES_MAP = Arrays.stream(ALL_VALUES)
			.collect(Collectors.toMap(JIDVisibility::getValue, Function.identity()));

	public static String[] getOptionValues() {
		return Arrays.stream(ALL_VALUES).map(JIDVisibility::getValue).toArray(String[]::new);
	}

	public static String[] getOptionLabels() {
		return Arrays.stream(ALL_VALUES).map(JIDVisibility::getLabel).toArray(String[]::new);
	}

	public static JIDVisibility parse(String str) {
		JIDVisibility result = ALL_VALUES_MAP.get(str);
		if (result == null) {
			throw new IllegalArgumentException(
					"No enum constant " + JIDVisibility.class.getCanonicalName() + "." + str);
		}
		return result;
	}

	private final String value;
	private final String label;

	private JIDVisibility(String value, String label) {
		this.value = value;
		this.label = label;
	}

	public String getLabel() {
		return label;
	}

	public String getValue() {
		return value;
	}
	
}
