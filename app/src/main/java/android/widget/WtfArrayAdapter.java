/*-
 * Copyright © 2016
 *	mirabilos <m@mirbsd.org>
 *
 * Provided that these terms and disclaimer and all copyright notices
 * are retained or reproduced in an accompanying document, permission
 * is granted to deal in this work without restriction, including un‐
 * limited rights to use, publicly perform, distribute, sell, modify,
 * merge, give away, or sublicence.
 *
 * This work is provided “AS IS” and WITHOUT WARRANTY of any kind, to
 * the utmost extent permitted by applicable law, neither express nor
 * implied; without malicious intent or gross negligence. In no event
 * may a licensor, author or contributor be held liable for indirect,
 * direct, other damage, loss, or other issues arising in any way out
 * of dealing in the work, even if advised of the possibility of such
 * damage or existence of a defect, except proven that it results out
 * of said person’s immediate fault when using the work as intended.
 *-
 * Partially derived from stock Android ArrayAdapter sources.
 */

package android.widget;

import android.content.Context;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;

import java.util.Arrays;

import de.naturalnet.mirwtfapp.MainActivity;

/**
 * Created by mirabilos on 26.04.16.
 */
public class WtfArrayAdapter<T> extends ArrayAdapter<T> {
    private MainActivity sParent;
    private WtfArrayFilter sFilter;
    private Filter pFilter;

    /**
     * Constructor
     *
     * @param context  The current context.
     * @param resource The resource ID for a layout file containing a TextView to use when
     *                 instantiating views.
     * @param objects  The objects to represent in the ListView.
     */
    public WtfArrayAdapter(Context context, @LayoutRes int resource, @NonNull T[] objects, MainActivity parent) {
        super(context, resource, 0, Arrays.asList(objects));
        sParent = parent;
        sFilter = new WtfArrayFilter();
        pFilter = super.getFilter();
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public Filter getFilter() {
        return sFilter;
    }

    /**
     * <p>An array filter constrains the content of the array adapter with
     * a prefix. Each item that does not start with the supplied prefix
     * is removed from the list.</p>
     */
    private class WtfArrayFilter extends Filter {
        @Override
        protected FilterResults performFiltering(CharSequence prefix) {
            return pFilter.performFiltering(prefix == null ? null : sParent.normaliseAcronym(prefix.toString()));
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            pFilter.publishResults(constraint, results);
        }
    }
}
