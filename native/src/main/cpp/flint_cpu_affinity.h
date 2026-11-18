#pragma once

#include <sched.h>

// Select every allowed CPU tied for the highest advertised maximum frequency.
// Returns false when sysfs has no usable frequency data; callers must leave the
// current affinity unchanged rather than guessing from CPU IDs.
bool flint_select_performance_cpus(
    int cpu_count,
    const cpu_set_t *allowed,
    const char *sysfs_root,
    cpu_set_t *selected,
    unsigned long *max_frequency
);
