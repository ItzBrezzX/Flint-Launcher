#include "flint_cpu_affinity.h"

#include <cerrno>
#include <cctype>
#include <cstdio>
#include <cstdlib>

namespace {
bool read_max_frequency(const char *root, int cpu, unsigned long *frequency) {
    char path[512];
    const int length = std::snprintf(
        path, sizeof(path), "%s/cpu%d/cpufreq/cpuinfo_max_freq", root, cpu
    );
    if (length < 0 || static_cast<size_t>(length) >= sizeof(path)) return false;

    FILE *file = std::fopen(path, "r");
    if (!file) return false;
    char buffer[64];
    const size_t count = std::fread(buffer, 1, sizeof(buffer) - 1, file);
    const bool read_error = std::ferror(file) != 0;
    std::fclose(file);
    if (read_error || count == 0 || count == sizeof(buffer) - 1) return false;
    buffer[count] = '\0';

    errno = 0;
    char *end = nullptr;
    const unsigned long parsed = std::strtoul(buffer, &end, 10);
    if (errno != 0 || end == buffer || parsed == 0) return false;
    while (*end && std::isspace(static_cast<unsigned char>(*end))) ++end;
    if (*end != '\0') return false;
    *frequency = parsed;
    return true;
}
} // namespace

bool flint_select_performance_cpus(
    int cpu_count,
    const cpu_set_t *allowed,
    const char *sysfs_root,
    cpu_set_t *selected,
    unsigned long *max_frequency
) {
    if (cpu_count <= 0 || !allowed || !sysfs_root || !selected || !max_frequency) {
        return false;
    }

    CPU_ZERO(selected);
    *max_frequency = 0;
    const int limit = cpu_count < CPU_SETSIZE ? cpu_count : CPU_SETSIZE;
    for (int cpu = 0; cpu < limit; ++cpu) {
        if (!CPU_ISSET(cpu, allowed)) continue;
        unsigned long frequency = 0;
        if (!read_max_frequency(sysfs_root, cpu, &frequency)) continue;

        if (frequency > *max_frequency) {
            CPU_ZERO(selected);
            *max_frequency = frequency;
        }
        if (frequency == *max_frequency) CPU_SET(cpu, selected);
    }
    return *max_frequency > 0 && CPU_COUNT(selected) > 0;
}
