package com.routepulse.app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.appcompat.widget.AppCompatButton;
import androidx.fragment.app.Fragment;



public class OnboardingFragment extends Fragment {

    private static final String ARG_PAGE = "page";
    private int pageNumber;
    private LinearLayout dotsLayout;

    public OnboardingFragment() {
        // Required empty public constructor
    }

    public static OnboardingFragment newInstance(int page) {
        OnboardingFragment fragment = new OnboardingFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_PAGE, page);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            pageNumber = getArguments().getInt(ARG_PAGE);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        int layoutResId;
        switch (pageNumber) {
            case 1:
                layoutResId = R.layout.onboarding_1;
                break;
            case 2:
                layoutResId = R.layout.onboarding_2;
                break;
            case 3:
                layoutResId = R.layout.onboarding_3;
                break;
            default:
                return null;
        }

        View view = inflater.inflate(layoutResId, container, false);
        dotsLayout = view.findViewById(R.id.dotsLayout);
        updateDots(viewPagerPosition());

        // Set up button listeners
        if (pageNumber == 1 || pageNumber == 2) {
            AppCompatButton continueBtn = view.findViewById(R.id.continueBtn);
            if (continueBtn != null) {
                continueBtn.setOnClickListener(v -> ((OnboardingActivity) requireActivity()).navigateToNextPage());
            }

            View skipText = view.findViewById(R.id.skipText);
            if (skipText != null) {
                skipText.setOnClickListener(v -> ((OnboardingActivity) requireActivity()).skipToLastPage());
            }
        } else if (pageNumber == 3) {
            AppCompatButton getStartedBtn = view.findViewById(R.id.getStartedBtn);
            if (getStartedBtn != null) {
                getStartedBtn.setOnClickListener(v -> ((OnboardingActivity) requireActivity()).finishOnboarding());
            }
        }

        return view;
    }

    public void updateDots(int currentPosition) {
        if (dotsLayout != null) {
            for (int i = 0; i < dotsLayout.getChildCount(); i++) {
                dotsLayout.getChildAt(i).setBackgroundResource(
                        i == currentPosition ? R.drawable.dot_active : R.drawable.dot_inactive
                );
            }
        }
    }

    private int viewPagerPosition() {
        return ((OnboardingActivity) requireActivity()).getViewPager().getCurrentItem();
    }
}