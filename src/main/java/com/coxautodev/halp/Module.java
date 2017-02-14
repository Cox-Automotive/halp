package com.coxautodev.halp;

import java.util.List;

public interface Module {
    String name();
    List<String> includes();
    List<String> uses();
}
