#include "flint_cpu_affinity.h"

#include <cassert>
#include <filesystem>
#include <fstream>
#include <string>
#include <unistd.h>

static void write_frequency(const std::filesystem::path &root, int cpu, const char *value) {
    auto path = root / ("cpu" + std::to_string(cpu)) / "cpufreq" / "cpuinfo_max_freq";
    std::filesystem::create_directories(path.parent_path());
    std::ofstream out(path);
    out << value << '\\n';
    assert(out.good());
}

int main() {
    char template_path[] = "/tmp/flint-cpu-affinity-XXXXXX";
    char *created = mkdtemp(template_path);
    assert(created != nullptr);
    const std::filesystem::path root(created);

    write_frequency(root, 0, "1400000");
    write_frequency(root, 1, "1400000");
    write_frequency(root, 2, "2000000");
    write_frequency(root, 3, "2000000");

    cpu_set_t allowed;
    CPU_ZERO(&allowed);
    for (int cpu = 0; cpu < 4; ++cpu) CPU_SET(cpu, &allowed);

    cpu_set_t selected;
    unsigned long max_frequency = 0;
    assert(flint_select_performance_cpus(4, &allowed, root.c_str(), &selected, &max_frequency));
    assert(max_frequency == 2000000UL);
    assert(CPU_COUNT(&selected) == 2);
    assert(CPU_ISSET(2, &selected));
    assert(CPU_ISSET(3, &selected));
    assert(!CPU_ISSET(0, &selected));
    assert(!CPU_ISSET(1, &selected));

    CPU_ZERO(&allowed);
    CPU_SET(0, &allowed);
    CPU_SET(2, &allowed);
    assert(flint_select_performance_cpus(4, &allowed, root.c_str(), &selected, &max_frequency));
    assert(max_frequency == 2000000UL);
    assert(CPU_COUNT(&selected) == 1);
    assert(CPU_ISSET(2, &selected));

    CPU_ZERO(&allowed);
    CPU_SET(7, &allowed);
    assert(!flint_select_performance_cpus(8, &allowed, root.c_str(), &selected, &max_frequency));
    assert(CPU_COUNT(&selected) == 0);

    std::filesystem::remove_all(root);
    return 0;
}
