package com.example.cpufreqread;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class CpuFrequencyViewModel extends ViewModel {
    public final MutableLiveData<String> cpuFrequencies = new MutableLiveData<>();

    public LiveData<String> getCpuFrequencies() {
        return cpuFrequencies;
    }

    public void setCpuFrequencies(String frequencies) {
        cpuFrequencies.setValue(frequencies);
    }
}
