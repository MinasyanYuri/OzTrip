package com.oztrip.armenia;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class TravelListSheetFragment extends BottomSheetDialogFragment {

    private RecyclerView recyclerView;
    private EditText etSearch;
    private LinearLayout headerLayout, selectionToolbar;
    private TextView tvSelectedCount;
    private View btnSelect, btnCancelSelection, btnDeleteSelected;
    private TravelListAdapter adapter;
    private List<TravelList> allLists;
    private int activeIndex;
    private List<Integer> indexMap = new ArrayList<>();

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        return new BottomSheetDialog(requireContext(), R.style.TransparentBottomSheet);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_travel_lists, container, false);

        recyclerView = view.findViewById(R.id.rvSheetTravelLists);
        etSearch = view.findViewById(R.id.etSearchList);
        headerLayout = view.findViewById(R.id.headerLayout);
        selectionToolbar = view.findViewById(R.id.selectionToolbar);
        tvSelectedCount = view.findViewById(R.id.tvSelectedCount);
        btnSelect = view.findViewById(R.id.btnSelect);
        btnCancelSelection = view.findViewById(R.id.btnCancelSelection);
        btnDeleteSelected = view.findViewById(R.id.btnDeleteSelected);

        MainActivity activity = (MainActivity) getActivity();
        if (activity != null) {
            allLists = new ArrayList<>(activity.getAllTravelLists());
            activeIndex = activity.getActiveListIndex();
        } else {
            allLists = new ArrayList<>();
            activeIndex = 0;
        }

        filterLists("");

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterLists(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        btnSelect.setOnClickListener(v -> {
            adapter.setSelectionMode(true);
            headerLayout.setVisibility(View.GONE);
            selectionToolbar.setVisibility(View.VISIBLE);
            tvSelectedCount.setText(getString(R.string.text_auto_181));
            btnDeleteSelected.setEnabled(false);
        });

        btnCancelSelection.setOnClickListener(v -> {
            adapter.setSelectionMode(false);
            headerLayout.setVisibility(View.VISIBLE);
            selectionToolbar.setVisibility(View.GONE);
        });

        btnDeleteSelected.setOnClickListener(v -> {
            if (adapter == null) return;
            Set<Integer> selected = adapter.getSelectedPositions();
            if (selected.isEmpty()) return;

            View dialogView = inflater.inflate(R.layout.dialog_confirm_delete, null);
            TextView tvMessage = dialogView.findViewById(R.id.tvConfirmMessage);
            tvMessage.setText(getString(R.string.text_auto_182) + selected.size() + getString(R.string.text_auto_183));

            AlertDialog confirmDialog = new AlertDialog.Builder(getActivity(), R.style.PremiumDialogTheme)
                    .setView(dialogView)
                    .create();

            dialogView.findViewById(R.id.btnConfirmDelete).setOnClickListener(btn -> {
                List<Integer> realIndices = new ArrayList<>();
                for (Integer pos : selected) {
                    realIndices.add(indexMap.get(pos));
                }
                Collections.sort(realIndices, Collections.reverseOrder());
                for (int idx : realIndices) {
                    ((MainActivity) getActivity()).removeTravelListByIndex(idx);
                }

                Toast.makeText(getActivity(), getString(R.string.text_auto_184) + realIndices.size() + getString(R.string.text_auto_185), Toast.LENGTH_SHORT).show();

                // Закрываем диалог
                confirmDialog.dismiss();

                // Обновляем данные
                allLists = new ArrayList<>(((MainActivity) getActivity()).getAllTravelLists());
                activeIndex = ((MainActivity) getActivity()).getActiveListIndex();
                filterLists(etSearch.getText().toString());

                // Сбрасываем режим выбора и показываем заголовок
                adapter.setSelectionMode(false);
                headerLayout.setVisibility(View.VISIBLE);
                selectionToolbar.setVisibility(View.GONE);
            });

            dialogView.findViewById(R.id.btnConfirmCancel).setOnClickListener(btn -> confirmDialog.dismiss());
            confirmDialog.show();
        });

        return view;
    }

    private void filterLists(String query) {
        if (allLists == null) return;
        List<TravelList> filtered = new ArrayList<>();
        indexMap.clear();
        for (int i = 0; i < allLists.size(); i++) {
            if (allLists.get(i).name.toLowerCase().contains(query.toLowerCase())) {
                filtered.add(allLists.get(i));
                indexMap.add(i);
            }
        }

        adapter = new TravelListAdapter(filtered, new TravelListAdapter.OnListClickListener() {
            @Override
            public void onListClick(int position) {
                if (adapter.isSelectionMode()) {
                    adapter.toggleSelection(position);
                    return;
                }
                int realIndex = indexMap.get(position);
                MainActivity act = (MainActivity) getActivity();
                if (act != null) act.switchTravelList(realIndex);
                dismiss();
            }

            @Override
            public void onListRename(int position, String oldName) {
                int realIndex = indexMap.get(position);
                MainActivity act = (MainActivity) getActivity();
                if (act != null) {
                    act.showRenameDialog(realIndex, oldName, () -> {
                        filterLists(etSearch.getText().toString());
                    });
                }
            }
        }, true);
        adapter.setOnSelectionModeChangeListener(enabled -> {
            if (enabled) {
                // Включаем режим выбора – скрываем заголовок, показываем панель
                headerLayout.setVisibility(View.GONE);
                selectionToolbar.setVisibility(View.VISIBLE);
                tvSelectedCount.setText(getString(R.string.text_auto_181));
                btnDeleteSelected.setEnabled(false);
            } else {
                // Выключаем режим – возвращаем заголовок, скрываем панель
                headerLayout.setVisibility(View.VISIBLE);
                selectionToolbar.setVisibility(View.GONE);
            }
        });
        adapter.setSelectionChangeListener(count -> {
            tvSelectedCount.setText(count + getString(R.string.text_auto_186));
            btnDeleteSelected.setEnabled(count > 0);
        });

        int activePos = indexMap.indexOf(activeIndex);
        adapter.setSelectedIndex(activePos);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);
    }
}