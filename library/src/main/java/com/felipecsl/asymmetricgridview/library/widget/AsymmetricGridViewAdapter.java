package com.felipecsl.asymmetricgridview.library.widget;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;

import com.felipecsl.asymmetricgridview.library.R;
import com.felipecsl.asymmetricgridview.library.Utils;
import com.felipecsl.asymmetricgridview.library.model.AsymmetricItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class AsymmetricGridViewAdapter<T
        extends AsymmetricItem> extends ArrayAdapter<T>
        implements View.OnClickListener {

    private class RowInfo {

        private final List<T> items;
        private final int rowHeight;
        private final float spaceLeft;

        public RowInfo(final int rowHeight,
                       final List<T> items,
                       final float spaceLeft) {
            this.rowHeight = rowHeight;
            this.items = items;
            this.spaceLeft = spaceLeft;
        }

        public List<T> getItems() {
            return items;
        }

        public int getRowHeight() {
            return rowHeight;
        }

        public float getSpaceLeft() {
            return spaceLeft;
        }
    }

    private static final String TAG = "AsymmetricGridViewAdapter";
    protected static final boolean DEBUG = false;
    protected final AsymmetricGridView listView;
    protected final Context context;
    protected final List<T> items;
    private final Map<Integer, RowInfo> itemsPerRow = new HashMap<>();

    public AsymmetricGridViewAdapter(final Context context,
                                     final AsymmetricGridView listView,
                                     final List<T> items) {

        super(context, 0, items);

        this.items = items;
        this.context = context;
        this.listView = listView;
    }

    public abstract View getActualView(final int position, final View convertView, final ViewGroup parent);

    protected int getRowHeight(final AsymmetricItem item) {
        final int rowHeight = listView.getColumnWidth() * item.getRowSpan();
        // when the item spans multiple rows, we need to account for the vertical padding
        // and add that to the total final height
        return rowHeight + ((item.getRowSpan() - 1) * listView.getRequestedVerticalSpacing());
    }

    protected int getRowWidth(final AsymmetricItem item) {
        final int rowWidth = listView.getColumnWidth() * item.getColumnSpan();
        // when the item spans multiple columns, we need to account for the horizontal padding
        // and add that to the total final width
        return Math.min(rowWidth + ((item.getColumnSpan() - 1) * listView.getRequestedHorizontalSpacing()), Utils.getScreenWidth(getContext()));
    }

    @Override
    public View getView(final int position, final View convertView, final ViewGroup parent) {
        LinearLayout layout = findOrInitializeLayout(convertView);

        final RowInfo rowInfo = itemsPerRow.get(position);
        final List<AsymmetricItem> rowItems = new ArrayList<>();
        rowItems.addAll(rowInfo.getItems());

        // Index to control the current position
        // of the current column in this row
        int columnIndex = 0;

        // Index to control the current position
        // in the array of all the items available for this row
        int currentIndex = 0;

        // Index to control the current position
        // within the current column
        int currentColumnIndex = 0;

        int spaceLeftInColumn = rowInfo.getRowHeight();

        while (!rowItems.isEmpty() && columnIndex < listView.getNumColumns()) {
            final AsymmetricItem currentItem = rowItems.get(currentIndex);

            if (spaceLeftInColumn == 0) {
                // No more space in this column. Move to next one
                columnIndex++;
                currentIndex = 0;
                currentColumnIndex = 0;
                spaceLeftInColumn = rowInfo.getRowHeight();
                continue;
            }

            // Is there enough space in this column to accommodate currentItem?
            if (spaceLeftInColumn >= currentItem.getRowSpan()) {
                rowItems.remove(currentItem);

                final LinearLayout childLayout = findOrInitializeChildLayout(layout, columnIndex);
                final View childConvertView = childLayout.getChildAt(currentColumnIndex);
                final View v = getActualView(items.indexOf(currentItem), childConvertView, parent);
                v.setTag(currentItem);
                v.setOnClickListener(this);

                currentColumnIndex += currentItem.getRowSpan();
                spaceLeftInColumn -= currentItem.getRowSpan();
                currentIndex = 0;

                if (childConvertView == null)
                    childLayout.addView(v);
            } else if (currentIndex < rowItems.size() - 1) {
                // Try again with next item
                currentIndex++;
            } else {
                break;
            }
        }

        return layout;
    }

    private LinearLayout findOrInitializeLayout(final View convertView) {
        LinearLayout layout;

        if (convertView == null) {
            layout = new LinearLayout(context);
            if (DEBUG)
                layout.setBackgroundColor(Color.parseColor("#00ff00"));

            if (Build.VERSION.SDK_INT >= 11) {
                layout.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
                layout.setDividerDrawable(context.getResources().getDrawable(R.drawable.item_divider_horizontal));
            }

            layout.setLayoutParams(new AbsListView.LayoutParams(
                    AbsListView.LayoutParams.MATCH_PARENT,
                    AbsListView.LayoutParams.WRAP_CONTENT));
        } else
            layout = (LinearLayout) convertView;

        // Clear all layout children before starting
        for (int j = 0; j < layout.getChildCount(); j++) {
            LinearLayout tempChild = (LinearLayout) layout.getChildAt(j);
            tempChild.removeAllViews();
        }
        layout.removeAllViews();

        return layout;
    }

    private LinearLayout findOrInitializeChildLayout(final LinearLayout parentLayout, final int childIndex) {
        LinearLayout childLayout = (LinearLayout) parentLayout.getChildAt(childIndex);

        if (childLayout == null) {
            childLayout = new LinearLayout(context);
            childLayout.setOrientation(LinearLayout.VERTICAL);

            if (DEBUG)
                childLayout.setBackgroundColor(Color.parseColor("#0000ff"));

            if (Build.VERSION.SDK_INT >= 11) {
                childLayout.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
                childLayout.setDividerDrawable(context.getResources().getDrawable(R.drawable.item_divider_vertical));
            }

            childLayout.setLayoutParams(new AbsListView.LayoutParams(
                    AbsListView.LayoutParams.WRAP_CONTENT,
                    AbsListView.LayoutParams.MATCH_PARENT));

            parentLayout.addView(childLayout);
        }

        return childLayout;
    }

    public void setItems(List<T> newItems) {
        items.clear();
        items.addAll(newItems);
        recalculateItemsPerRow();
        notifyDataSetChanged();
    }

    public void appendItems(List<T> newItems) {
        items.addAll(newItems);

        final int lastRow = getCount() - 1;
        final RowInfo rowInfo = itemsPerRow.get(lastRow);

        if (rowInfo != null) {
            final float spaceLeftInLastRow = rowInfo.getSpaceLeft();

            if (DEBUG)
                Log.d(TAG, "Space left in last row: " + spaceLeftInLastRow);

            // Try to add new items into the last row, if there is any space left
            if (spaceLeftInLastRow > 0) {

                for (final T i : rowInfo.getItems())
                    newItems.add(0, i);

                final RowInfo stuffThatFit = calculateItemsForRow(newItems);
                final List<T> itemsThatFit = stuffThatFit.getItems();

                if (!itemsThatFit.isEmpty()) {
                    for (int i = 0; i < itemsThatFit.size(); i++)
                        newItems.remove(itemsThatFit.get(i));

                    itemsPerRow.put(lastRow, stuffThatFit);
                }
            }
        }
        calculateItemsPerRow(getCount(), newItems);
        notifyDataSetChanged();
    }

    @Override
    public void onClick(final View v) {
        final AsymmetricItem item = (AsymmetricItem) v.getTag();
        listView.fireOnItemClick(items.indexOf(item), v);
    }

    @Override
    public int getCount() {
        // Returns the row count for ListView display purposes
        return itemsPerRow.size();
    }

    public void recalculateItemsPerRow() {
        itemsPerRow.clear();
        final List<T> itemsToAdd = new ArrayList<>();
        itemsToAdd.addAll(items);
        calculateItemsPerRow(0, itemsToAdd);
    }

    private void calculateItemsPerRow(int currentRow, List<T> itemsToAdd) {
        while (!itemsToAdd.isEmpty()) {
            final RowInfo stuffThatFit = calculateItemsForRow(itemsToAdd);

            final List<T> itemsThatFit = stuffThatFit.getItems();
            if (itemsThatFit.isEmpty()) {
                // we can't fit a single item inside a row.
                // bail out.
                break;
            }

            for (int i = 0; i < itemsThatFit.size(); i++)
                itemsToAdd.remove(itemsThatFit.get(i));

            itemsPerRow.put(currentRow, stuffThatFit);
            currentRow++;
        }

        if (DEBUG) {
            for (Map.Entry<Integer, RowInfo> e : itemsPerRow.entrySet())
                Log.d(TAG, "row: " + e.getKey() + ", items: " + e.getValue().getItems().size());
        }
    }

    private RowInfo calculateItemsForRow(final List<T> items) {
        return calculateItemsForRow(items, listView.getNumColumns());
    }

    private RowInfo calculateItemsForRow(final List<T> items, final float initialSpaceLeft) {
        final List<T> itemsThatFit = new ArrayList<>();
        int currentItem = 0;
        int rowHeight = 1;
        float spaceLeft = initialSpaceLeft;

        while (spaceLeft > 0 && currentItem < items.size()) {
            final T item = items.get(currentItem++);

            if (item.getColumnSpan() == 1) {
                // 1x sized items
                float spaceConsumption = (float) (1.0 / rowHeight);

                if (spaceLeft >= spaceConsumption) {
                    spaceLeft -= spaceConsumption;
                    itemsThatFit.add(item);
                }
            } else {
                // 2x sizes items
                float spaceConsumption = 2;

                if (rowHeight == 1) {
                    // restart with double height
                    itemsThatFit.clear();
                    rowHeight = 2;
                    currentItem = 0;
                    spaceLeft = initialSpaceLeft;
                } else if (spaceLeft >= spaceConsumption) {
                    spaceLeft -= spaceConsumption;
                    itemsThatFit.add(item);
                } else if (!listView.isAllowReordering()) {
                    break;
                }
            }
        }

        return new RowInfo(rowHeight, itemsThatFit, spaceLeft);
    }
}
