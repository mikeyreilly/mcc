struct Pixel {
	unsigned short channels[5];
};

struct Tile {
	char lead;
	struct Pixel pixels[4];
};

struct Picture {
	int id;
	struct Tile tiles[3];
};

static unsigned long nested_designator_offset(int tile, int pixel, int channel) {
	return __builtin_offsetof(struct Picture,
			tiles[tile].pixels[pixel].channels[channel]);
}

static unsigned long runtime_stride_offset(int columns, int row, int column) {
	typedef unsigned long RuntimeRow[columns];
	struct RuntimeGrid {
		char tag;
		RuntimeRow rows[3];
	};
	return __builtin_offsetof(struct RuntimeGrid, rows[row][column]);
}

int main(void) {
	if (nested_designator_offset(2, 3, 4) != 128) {
		return 1;
	}

	int tile = 1;
	if (__builtin_offsetof(struct Picture,
			tiles[tile++].pixels[0].channels[0]) != 48) {
		return 2;
	}
	if (tile != 2) {
		return 3;
	}

	if (runtime_stride_offset(7, 2, 5) != 160) {
		return 4;
	}
	return 0;
}
