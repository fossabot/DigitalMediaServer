/*
 * Digital Media Server, for streaming digital media to UPnP AV or DLNA
 * compatible devices based on PS3 Media Server and Universal Media Server.
 * Copyright (C) 2016 Digital Media Server developers.
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see http://www.gnu.org/licenses/.
 */
package net.pms.newgui.components;

import java.net.URL;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import javax.swing.Icon;
import javax.swing.UIManager;
import net.pms.newgui.LooksFrame;
import net.pms.newgui.components.AnimatedIcon.AnimatedIconStage;
import net.pms.util.FileUtil;

/**
 * An {@link ImageButton} that implements {@link AnimatedComponent} so that it
 * can handle {@link AnimatedIcon}s.
 *
 * @author Nadahar
 */
@NotThreadSafe
public class AnimatedButton extends ImageButton implements AnimatedComponent {

	private static final long serialVersionUID = 1L;

	private AnimatedIcon currentIcon = null;

	/**
	 * Creates a new instance with the specified {@link AnimatedIcon} and text.
	 *
	 * @param text the text to use.
	 * @param icon the {@link AnimatedIcon} to use.
	 */
	public AnimatedButton(String text, AnimatedIcon icon) {
		super(text, icon);
	}

	/**
	 * Creates a new instance with the specified {@link AnimatedIcon}.
	 *
	 * @param icon the {@link AnimatedIcon} to use.
	 */
	public AnimatedButton(AnimatedIcon icon) {
		super(icon);
	}

	/**
	 * Creates a new instance with the specified text and icon(s).
	 *
	 * @param text the text to use.
	 * @param defaultIconName the base image resource name used when the button
	 *            is in the normal state and from which the other state names
	 *            are derived from. See {@link ImageButton} for name convention
	 *            description.
	 */
	public AnimatedButton(
		@Nullable String text,
		@Nullable String defaultIconName
	) {
		super(text, (Icon) null);
		setProperites();
		setIcons(defaultIconName);
	}

	/**
	 * Creates a new instance with the specified icon(s).
	 *
	 * @param defaultIconName the base image resource name used when the button
	 *            is in the normal state and from which the other state names
	 *            are derived from. See {@link ImageButton} for name convention
	 *            description.
	 */
	public AnimatedButton(
		@Nullable String defaultIconName
	) {
		setProperites();
		setIcons(defaultIconName);
	}

	/**
	 * Creates a new instance with no icons or text set.
	 */
	public AnimatedButton() {
		super();
	}

	private AnimatedIcon readAnimatedIcon(String filename) {
		URL url = LooksFrame.class.getResource("/resources/images/" + filename);
		return url == null ? null : new AnimatedIcon(this, filename);
	}

	/**
	 * Set static icons from standard naming convention that is of type
	 * {@link AnimatedIcon}. While this can seem unnecessary it means that they
	 * can handle transitions to and from other (animated) {@link AnimatedIcon}s
	 * and thus be used on a {@link AnimatedButton}.
	 *
	 * @param defaultIconName the base image resource name used when the button
	 *            is in the normal state and which the other state names are
	 *            derived from.
	 */
	@Override
	public void setIcons(@Nullable String defaultIconName) {
		if (defaultIconName == null) {
			return;
		}

		AnimatedIcon icon = readAnimatedIcon(defaultIconName);
		if (icon == null) {
			setIcon(UIManager.getIcon("OptionPane.warningIcon"));
			return;
		}
		setIcon(icon);

		icon = readAnimatedIcon(FileUtil.appendToFileName(defaultIconName, "_pressed"));
		if (icon != null) {
			setPressedIcon(icon);
		}

		icon = readAnimatedIcon(FileUtil.appendToFileName(defaultIconName, "_disabled"));
		if (icon != null) {
			setDisabledIcon(icon);
		}

		icon = readAnimatedIcon(FileUtil.appendToFileName(defaultIconName, "_mouseover"));
		if (icon != null) {
			setRolloverIcon(icon);
		}
	}

	@Override
	public AnimatedIcon getCurrentIcon() {
		return currentIcon;
	}

	@Override
	public void setCurrentIcon(AnimatedIcon icon) {
		currentIcon = icon;
	}

	@Override
	public void setNextIcon(AnimatedIconStage stage) {
		switch (stage.iconType) {
			case PRESSEDICON:
				setPressedIcon(stage.icon);
				break;
			case DISABLEDICON:
				setDisabledIcon(stage.icon);
				break;
			case SELECTEDICON:
				setSelectedIcon(stage.icon);
				break;
			case DISABLEDSELECTEDICON:
				setDisabledSelectedIcon(stage.icon);
				break;
			case ROLLOVERICON:
				setRolloverIcon(stage.icon);
				break;
			case ROLLOVERSELECTEDICON:
				setRolloverSelectedIcon(stage.icon);
				break;
			default:
				setIcon(stage.icon);
				break;
		}
	}
}
