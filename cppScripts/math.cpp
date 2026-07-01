extern "C" {

// @mite
int add(int a, int b) {
    return a + b;
}

// @mite
float sum(const float* values, int size) {
    float total = 0.0f;
    for (int i = 0; i < size; i++) {
        total += values[i];
    }
    return total;
}

}