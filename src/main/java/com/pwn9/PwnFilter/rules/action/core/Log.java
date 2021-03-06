/*
 * PwnFilter -- Regex-based User Filter Plugin for Bukkit-based Minecraft servers.
 * Copyright (c) 2013 Pwn9.com. Tremor77 <admin@pwn9.com> & Sage905 <patrick@toal.ca>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 */

package com.pwn9.PwnFilter.rules.action.core;

import com.pwn9.PwnFilter.FilterTask;
import com.pwn9.PwnFilter.rules.action.Action;

/**
 * Log this event.
 *
 * @author ptoal
 * @version $Id: $Id
 */
@SuppressWarnings("UnusedDeclaration")
public class Log implements Action {

    /** {@inheritDoc} */
    public void init(String s)
    {
        // Do nothing with a string, if one is provided.
    }

    /** {@inheritDoc} */
    public void execute(final FilterTask filterTask) {
        filterTask.setLogging();
    }
}
