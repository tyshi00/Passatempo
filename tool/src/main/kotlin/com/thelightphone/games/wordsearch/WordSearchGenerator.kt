package com.thelightphone.games.wordsearch

import kotlin.random.Random

/** A word placed on the grid, recorded as its ordered list of (row, col) cells. */
data class PlacedWord(
    val word: String,
    val startRow: Int,
    val startCol: Int,
    val dRow: Int,
    val dCol: Int,
) {
    val cells: List<Pair<Int, Int>> =
        word.indices.map { i -> (startRow + dRow * i) to (startCol + dCol * i) }
}

class WordSearchPuzzle(
    val size: Int,
    val grid: Array<CharArray>,
    val words: List<PlacedWord>,
)

object WordSearchGenerator {

    // All 8 compass directions, so words can run in any straight line (incl. diagonals, reversed).
    private val DIRECTIONS = listOf(
        0 to 1, 1 to 0, 1 to 1, -1 to 1,
        0 to -1, -1 to 0, -1 to -1, 1 to -1,
    )

    private val WORD_BANK = listOf(
        // Animals
        "LION", "TIGER", "ZEBRA", "GIRAFFE", "ELEPHANT", "PANDA", "KOALA", "KANGAROO",
        "CHEETAH", "LEOPARD", "JAGUAR", "GORILLA", "CHIMPANZEE", "OCELOT", "MEERKAT", "OTTER",
        "BEAVER", "RACCOON", "SQUIRREL", "HEDGEHOG", "PORCUPINE", "ARMADILLO", "ANTEATER", "SLOTH",
        "TAPIR", "BISON", "BUFFALO", "ANTELOPE", "GAZELLE", "IMPALA", "WILDEBEEST", "HYENA",
        "JACKAL", "MONGOOSE", "WOLVERINE", "LYNX", "BOBCAT", "COUGAR", "PUMA", "CARIBOU",
        "MOOSE", "ALPACA", "LLAMA", "CAMEL", "DONKEY", "MULE", "RHINOCEROS", "CROCODILE",
        "ALLIGATOR", "IGUANA", "CHAMELEON", "GECKO", "TORTOISE", "TURTLE", "PYTHON", "COBRA",
        "VIPER", "ANACONDA", "FLAMINGO", "PELICAN", "TOUCAN", "PARROT", "MACAW", "PEACOCK",
        "OSTRICH", "PENGUIN", "ALBATROSS", "FALCON", "EAGLE", "HAWK", "RAVEN", "CARDINAL",
        "BLUEJAY", "HUMMINGBIRD", "WOODPECKER", "KINGFISHER", "PUFFIN", "SEAGULL", "NARWHAL", "WALRUS",
        "OCTOPUS", "JELLYFISH", "STARFISH", "SEAHORSE", "LOBSTER", "STINGRAY", "PIRANHA", "CATFISH",
        "MACKEREL", "SWORDFISH", "DOLPHIN", "SPARROW",
        // Food
        "PANCAKE", "WAFFLE", "OMELET", "BURRITO", "TACO", "ENCHILADA", "QUESADILLA", "TAMALE",
        "CEVICHE", "PAELLA", "RISOTTO", "LASAGNA", "RAVIOLI", "GNOCCHI", "CANNOLI", "TIRAMISU",
        "BAGUETTE", "CROISSANT", "QUICHE", "RATATOUILLE", "FONDUE", "PRETZEL", "STRUDEL", "SCHNITZEL",
        "BRATWURST", "HUMMUS", "FALAFEL", "SHAWARMA", "KEBAB", "BAKLAVA", "COUSCOUS", "TAGINE",
        "CURRY", "MASALA", "BIRYANI", "SAMOSA", "CHUTNEY", "NAAN", "DUMPLING", "NOODLES",
        "RAMEN", "SUSHI", "SASHIMI", "TEMPURA", "TERIYAKI", "MISO", "KIMCHI", "BIBIMBAP",
        "PADTHAI", "PHO", "MANGO", "PAPAYA", "GUAVA", "LYCHEE", "POMEGRANATE", "PINEAPPLE",
        "COCONUT", "PLANTAIN", "CASSAVA", "YAM", "JACKFRUIT", "DURIAN", "PERSIMMON", "APRICOT",
        "NECTARINE", "CANTALOUPE", "HONEYDEW", "CRANBERRY", "BLUEBERRY", "RASPBERRY", "STRAWBERRY", "BLACKBERRY",
        "BREAD",
        // Vacation & travel
        "PASSPORT", "LUGGAGE", "SUITCASE", "BACKPACK", "ITINERARY", "SOUVENIR", "POSTCARD", "CAMPSITE",
        "HAMMOCK", "SNORKEL", "KAYAK", "CANOE", "CRUISE", "FERRY", "AIRPORT", "TERMINAL",
        "LAYOVER", "HOSTEL", "RESORT", "BEACH", "COASTLINE", "BOARDWALK", "LIGHTHOUSE", "CAMPFIRE",
        "TENT", "CAMPGROUND", "TRAILHEAD", "COMPASS", "BINOCULARS", "ADVENTURE", "EXCURSION", "SIGHTSEEING",
        "LANDMARK", "MUSEUM", "GALLERY", "MONUMENT", "CATHEDRAL", "TEMPLE", "SHRINE", "PALACE",
        "CASTLE", "FORTRESS", "CITADEL", "PLAZA", "MARKETPLACE", "BAZAAR", "CARAVAN", "VOYAGE",
        "JOURNEY",
        // Places (countries)
        "JAPAN", "CHINA", "INDIA", "KENYA", "GHANA", "NIGERIA", "EGYPT", "MOROCCO",
        "ETHIOPIA", "TANZANIA", "UGANDA", "ZAMBIA", "BOTSWANA", "NAMIBIA", "SENEGAL", "ALGERIA",
        "TUNISIA", "ANGOLA", "RWANDA", "SOMALIA", "ERITREA", "MALAWI", "ZIMBABWE", "MOZAMBIQUE",
        "CAMEROON", "LIBERIA", "GABON", "TOGO", "BENIN", "GUINEA", "MALI", "NIGER",
        "CHAD", "SUDAN", "VIETNAM", "THAILAND", "CAMBODIA", "MYANMAR", "MALAYSIA", "INDONESIA",
        "PHILIPPINES", "SINGAPORE", "MONGOLIA", "NEPAL", "BHUTAN", "BANGLADESH", "PAKISTAN", "JORDAN",
        "LEBANON", "SYRIA", "YEMEN", "OMAN", "QATAR", "KUWAIT", "BAHRAIN", "PALESTINE",
        "TURKEY", "ARMENIA", "GEORGIA", "AZERBAIJAN", "KAZAKHSTAN", "UZBEKISTAN", "MEXICO", "GUATEMALA",
        "HONDURAS", "NICARAGUA", "PANAMA", "COLOMBIA", "VENEZUELA", "ECUADOR", "PERU", "BOLIVIA",
        "PARAGUAY", "URUGUAY", "ARGENTINA", "BRAZIL", "CHILE", "CUBA", "JAMAICA", "HAITI",
        "BARBADOS", "BAHAMAS", "TRINIDAD", "FIJI", "SAMOA", "TONGA", "VANUATU", "AUSTRALIA",
        "CANADA", "ICELAND", "FINLAND", "NORWAY", "SWEDEN", "DENMARK", "IRELAND", "SCOTLAND",
        "PORTUGAL", "GREECE", "CROATIA", "SERBIA", "POLAND", "ROMANIA", "BULGARIA", "UKRAINE",
        "FRANCE", "GERMANY", "ITALY", "SPAIN",
        // Places (cities & landmarks)
        "NAIROBI", "LAGOS", "CAIRO", "MARRAKESH", "CASABLANCA", "ACCRA", "DAKAR", "KAMPALA",
        "MUMBAI", "DELHI", "BANGKOK", "JAKARTA", "MANILA", "HANOI", "SEOUL", "TOKYO",
        "KYOTO", "OSAKA", "BEIJING", "SHANGHAI", "HONGKONG", "ISTANBUL", "DUBAI", "DOHA",
        "RIYADH", "AMMAN", "BEIRUT", "JERUSALEM", "ATHENS", "ROME", "PARIS", "LONDON",
        "DUBLIN", "LISBON", "MADRID", "BARCELONA", "BERLIN", "VIENNA", "PRAGUE", "BUDAPEST",
        "WARSAW", "MOSCOW", "HAVANA", "KINGSTON", "BOGOTA", "LIMA", "SANTIAGO", "SAOPAULO",
        "TORONTO", "MONTREAL", "SYDNEY", "MELBOURNE", "AUCKLAND",
        // Geography features
        "MOUNTAIN", "VOLCANO", "GLACIER", "CANYON", "PLATEAU", "VALLEY", "DESERT", "SAVANNA",
        "TUNDRA", "RAINFOREST", "WETLAND", "MARSH", "SWAMP", "DELTA", "ESTUARY", "PENINSULA",
        "ARCHIPELAGO", "ISTHMUS", "FJORD", "LAGOON", "REEF", "ATOLL", "DUNE", "OASIS",
        "PLAIN", "PRAIRIE", "STEPPE", "HIGHLAND", "LOWLAND", "RIDGE", "SUMMIT", "CLIFF",
        "CAVERN", "GORGE", "WATERFALL", "GEYSER", "CRATER", "EQUATOR", "TROPICS", "HEMISPHERE",
        "CONTINENT",
        // Holidays (global)
        "DIWALI", "HOLI", "RAMADAN", "HANUKKAH", "PASSOVER", "KWANZAA", "NOWRUZ", "JUNETEENTH",
        "OKTOBERFEST", "MIDSUMMER", "SONGKRAN", "CHUSEOK", "NAVRATRI", "ONAM", "JUNKANOO", "CARNIVAL",
        "VESAK", "OBON", "EASTER", "CHRISTMAS", "HALLOWEEN", "NEWYEAR", "TET", "BASTILLE",
        "POSADA",
        // Notable people of color (surnames)
        "MANDELA", "TUBMAN", "DOUGLASS", "PARKS", "HAMER", "BALDWIN", "HURSTON", "ANGELOU",
        "MORRISON", "HUGHES", "WELLS", "TRUTH", "BETHUNE", "CARVER", "BANNEKER", "CHISHOLM",
        "BUNCHE", "MARSHALL", "RICE", "OBAMA", "ALI", "ROBINSON", "OWENS", "ASHE",
        "JOYNER", "BOLT", "WILLIAMS", "ARMSTRONG", "ELLINGTON", "FITZGERALD", "HOLIDAY", "FRANKLIN",
        "MARLEY", "WONDER", "HENDRIX", "BASQUIAT", "LAWRENCE", "GANDHI", "YOUSAFZAI", "HUERTA",
        "RIVERA", "KAHLO", "MARQUEZ", "NERUDA", "CONFUCIUS", "TAGORE", "SUZUKI",
        // Nature & weather
        "RAINBOW", "THUNDER", "LIGHTNING", "BLIZZARD", "HURRICANE", "TORNADO", "MONSOON", "DROUGHT",
        "AVALANCHE", "EARTHQUAKE", "TSUNAMI", "ECLIPSE", "METEOR", "COMET", "NEBULA", "GALAXY",
        "AURORA", "TWILIGHT", "SUNRISE", "SUNSET", "HORIZON", "BREEZE", "GALE", "FROST",
        "MIST", "FOG", "HAIL", "SLEET", "DRIZZLE",
        // Everyday / general
        "PENCIL", "CANDLE", "LANTERN", "PUZZLE", "MARBLE", "PEBBLE", "CRYSTAL", "VELVET",
        "WHISTLE", "BLOSSOM", "MEADOW", "HARBOR", "BRIDGE", "CHAIR", "GARDEN", "WINTER",
        "ISLAND", "FOREST", "PLANET", "ROCKET", "GUITAR", "PIANO", "UMBRELLA", "TELESCOPE",
        "MICROSCOPE", "HOURGLASS", "ANCHOR", "TREASURE", "OCEAN", "CLOUD", "APPLE", "RIVER",
        // Animals (more)
        "WOMBAT", "PLATYPUS", "DINGO", "QUOKKA", "CASSOWARY", "KIWI", "KOOKABURRA", "WALLABY",
        "BANDICOOT", "NUMBAT", "BILBY", "ECHIDNA", "MANATEE", "DUGONG", "BELUGA", "ORCAWHALE",
        "PORPOISE", "BARRACUDA", "MARLIN", "GROUPER", "HALIBUT", "FLOUNDER", "SNAPPER", "TILAPIA",
        "CARP", "MINNOW", "GUPPY", "CHINCHILLA", "GERBIL", "HAMSTER", "FERRET", "WEASEL",
        "STOAT", "MARTEN", "BADGER", "SKUNK", "OPOSSUM", "CAPYBARA", "AGOUTI", "CHIPMUNK",
        "MARMOT", "GROUNDHOG",
        // Food (more)
        "BORSCHT", "GOULASH", "PIEROGI", "STROGANOFF", "BRIOCHE", "CREPE", "ECLAIR", "MACARON",
        "PROFITEROLE", "FOCACCIA", "CIABATTA", "PITA", "FLATBREAD", "CORNBREAD", "BISCUIT", "SCONE",
        "MUFFIN", "DONUT", "CUPCAKE", "BROWNIE", "FUDGE", "TOFFEE", "CARAMEL", "NOUGAT",
        "MARZIPAN", "MERINGUE", "SORBET", "GELATO", "POPSICLE", "MILKSHAKE", "SMOOTHIE", "LEMONADE",
        "ICEDTEA", "ESPRESSO", "CAPPUCCINO", "LATTE", "MOCHA", "CIDER", "EGGNOG", "PUNCH",
        "SANGRIA", "MOJITO",
        // Sports
        "SOCCER", "BASKETBALL", "BASEBALL", "FOOTBALL", "HOCKEY", "CRICKET", "RUGBY", "TENNIS",
        "GOLF", "BOXING", "WRESTLING", "FENCING", "ARCHERY", "BADMINTON", "SQUASH", "BOWLING",
        "BILLIARDS", "DARTS", "CURLING", "LACROSSE", "VOLLEYBALL", "HANDBALL", "WATERPOLO", "ROWING",
        "SAILING", "SURFING", "SKATEBOARD", "SNOWBOARD", "SKIING", "LUGE", "BOBSLED", "SKELETON",
        "BIATHLON",
        // Music & instruments
        "VIOLIN", "CELLO", "VIOLA", "HARP", "TRUMPET", "TROMBONE", "CLARINET", "OBOE",
        "BASSOON", "FLUTE", "SAXOPHONE", "ACCORDION", "HARMONICA", "UKULELE", "BANJO", "MANDOLIN",
        "SITAR", "DIDGERIDOO", "BAGPIPES", "XYLOPHONE", "MARIMBA", "TIMPANI", "TAMBOURINE", "CASTANETS",
        "TRIANGLE", "CYMBAL", "DRUMKIT", "SYNTHESIZER",
        // Space & astronomy
        "QUASAR", "PULSAR", "ASTEROID", "METEORITE", "SATELLITE", "OBSERVATORY", "ASTRONAUT", "COSMONAUT",
        "SPACESHIP", "SHUTTLE", "ORBIT", "GRAVITY", "ATMOSPHERE", "EXOSPHERE", "MERCURY", "VENUS",
        "MARS", "JUPITER", "SATURN", "URANUS", "NEPTUNE", "PLUTO",
        // Professions
        "DOCTOR", "NURSE", "SURGEON", "DENTIST", "PHARMACIST", "THERAPIST", "ARCHITECT", "ENGINEER",
        "PLUMBER", "ELECTRICIAN", "CARPENTER", "MECHANIC", "WELDER", "BLACKSMITH", "TAILOR", "COBBLER",
        "BAKER", "BUTCHER", "FARMER", "FISHERMAN", "TEACHER", "PROFESSOR", "LIBRARIAN", "JOURNALIST",
        "EDITOR", "PUBLISHER", "PAINTER", "SCULPTOR", "MUSICIAN", "ACTOR", "DIRECTOR", "PRODUCER",
        // Transportation
        "BICYCLE", "MOTORCYCLE", "SCOOTER", "TRICYCLE", "UNICYCLE", "AUTOMOBILE", "TRUCK", "VAN",
        "TRAILER", "TRACTOR", "BULLDOZER", "FORKLIFT", "AMBULANCE", "FIRETRUCK", "TAXI", "LIMOUSINE",
        "RICKSHAW", "SUBWAY", "MONORAIL", "TRAM", "STREETCAR", "LOCOMOTIVE", "FREIGHT", "CARGO",
        "RAFT",
        // Mythology (global)
        "ZEUS", "ATHENA", "APOLLO", "ARTEMIS", "POSEIDON", "HERMES", "ARES", "APHRODITE",
        "HERA", "HADES", "ODIN", "THOR", "LOKI", "FREYA", "BALDER", "HEIMDALL",
        "VALKYRIE", "VALHALLA", "YGGDRASIL", "RAGNAROK", "ANUBIS", "OSIRIS", "ISIS", "HORUS",
        "SPHINX", "PHARAOH", "SCARAB",
        // Trees & plants
        "OAK", "MAPLE", "BIRCH", "WILLOW", "CEDAR", "SPRUCE", "PINE", "FIR",
        "REDWOOD", "SEQUOIA", "MAGNOLIA", "DOGWOOD", "SYCAMORE", "ELM", "ASH", "CHESTNUT",
        "WALNUT", "HICKORY", "POPLAR", "ALDER", "BAMBOO", "PALM",
        // Insects
        "BUTTERFLY", "DRAGONFLY", "LADYBUG", "BEETLE", "GRASSHOPPER", "CICADA", "MANTIS", "TERMITE",
        "WASP", "HORNET", "BUMBLEBEE", "FIREFLY", "MOSQUITO", "GNAT", "MOTH", "APHID",
        // Gemstones & minerals
        "DIAMOND", "RUBY", "EMERALD", "SAPPHIRE", "TOPAZ", "AMETHYST", "GARNET", "OPAL",
        "PEARL", "JADE", "ONYX", "QUARTZ", "AGATE", "TURQUOISE", "AQUAMARINE", "CITRINE",
        // Clothing & accessories
        "SWEATER", "JACKET", "BLAZER", "CARDIGAN", "HOODIE", "VEST", "PONCHO", "CLOAK",
        "CAPE", "OVERCOAT", "TROUSERS", "JEANS", "SHORTS", "LEGGINGS", "OVERALLS", "JUMPSUIT",
        "KIMONO", "SARI", "TURBAN", "SOMBRERO", "BERET", "FEDORA",
        // Places (more countries)
        "LATVIA", "LITHUANIA", "ESTONIA", "SLOVAKIA", "SLOVENIA", "MONTENEGRO", "KOSOVO", "MOLDOVA",
        "BELARUS", "ANDORRA", "MONACO", "MALTA", "CYPRUS", "LUXEMBOURG", "SANMARINO", "VATICAN",
        "BOSNIA", "MACEDONIA", "ALBANIA", "LAOS", "BRUNEI", "TIMOR", "MALDIVES", "SEYCHELLES",
        "MAURITIUS",
        // Places (more cities)
        "ABUJA", "KHARTOUM", "ALGIERS", "TRIPOLI", "TUNIS", "RABAT", "LUANDA", "MAPUTO",
        "HARARE", "LUSAKA", "GABORONE", "WINDHOEK", "PORTLOUIS", "VICTORIA", "COTONOU", "LOME",
        "OUAGADOUGOU", "BAMAKO", "NIAMEY", "DHAKA", "ISLAMABAD", "KABUL", "TEHRAN", "BAGHDAD",
        "DAMASCUS", "SANAA", "MUSCAT",
        // Holidays (more, global)
        "EPIPHANY", "ASSUMPTION", "ALLSAINTS", "CANDLEMAS", "PENTECOST", "ADVENT", "LENT", "PALMSUNDAY",
        "GOODFRIDAY", "QINGMING", "DUANWU", "MIDAUTUMN", "DOUBLENINTH", "HINAMATSURI", "SHICHIGOSAN",
        // Notable people of color (more surnames)
        "KEYS", "BEYONCE", "RIHANNA", "USHER", "PRINCE", "COSBY", "POITIER", "WASHINGTON",
        "FREEMAN", "GOLDBERG", "WINFREY", "LOVELACE", "JEMISON", "JOHNSON", "HAMILTON", "CATLETT",
        "COLEMAN", "LATIMER", "MCCOY", "JULIAN", "DREW", "MORGAN",
        // Geography (more features)
        "ICEBERG", "MARSHLAND", "BADLANDS", "MESA", "BUTTE", "RAVINE", "BLUFF", "FOOTHILLS",
        "WATERSHED", "TRIBUTARY",
        // Nature (more)
        "STALACTITE", "STALAGMITE", "GROTTO", "SINKHOLE", "PERMAFROST", "GLACIATION", "EROSION", "SEDIMENT",
        "MINERAL", "POLLINATION",
        // School & education
        "CLASSROOM", "CHALKBOARD", "BLACKBOARD", "TEXTBOOK", "NOTEBOOK", "CRAYON", "MARKER", "ERASER",
        "RULER", "PROTRACTOR", "CALCULATOR", "BEAKER", "TESTUBE", "FLASK",
        // Furniture
        "SOFA", "COUCH", "RECLINER", "OTTOMAN", "LOVESEAT", "SECTIONAL", "FUTON", "DAYBED",
        "BENCH", "ARMCHAIR", "ROCKER", "STOOL", "BARSTOOL", "CHAISE", "HASSOCK", "CREDENZA",
        "SIDEBOARD", "BUFFET", "HUTCH", "CABINET", "DRESSER", "WARDROBE", "ARMOIRE", "BOOKCASE",
        "BOOKSHELF", "SHELVING", "ETAGERE", "DESK", "ROLLTOP", "TABLE", "NIGHTSTAND", "ENDTABLE",
        "COFFEETABLE", "CONSOLE", "VANITY", "HEADBOARD", "FOOTBOARD", "BEDFRAME", "BUNKBED", "CRADLE",
        "BASSINET", "PLAYPEN", "HIGHCHAIR", "RECLINING", "FOOTSTOOL", "DIVAN", "SETTEE", "CHIFFONIER",
        "TRUNK",
        // Art supplies
        "PALETTE", "EASEL", "CANVAS", "BRUSH", "CHARCOAL", "PASTEL", "CHALK", "GRAPHITE",
        "INK", "STENCIL", "SPONGE", "VARNISH", "GESSO", "LINSEED", "TURPENTINE", "FIXATIVE",
        "PRIMER", "SKETCHPAD", "SKETCHBOOK", "PORTFOLIO", "MATBOARD", "FRAME", "MOUNTING", "ADHESIVE",
        "GLUE", "SCISSORS", "BLADE", "TEMPLATE", "TRACING", "PARCHMENT", "VELLUM", "GOUACHE",
        "TEMPERA", "ACRYLIC", "WATERCOLOR", "OILPAINT", "ENAMEL", "LACQUER", "GLITTER", "SEQUIN",
        "RIBBON", "YARN", "THREAD", "BEADS",
        // Art styles
        "CUBISM", "SURREALISM", "REALISM", "ROMANTICISM", "BAROQUE", "RENAISSANCE", "MINIMALISM", "MAXIMALISM",
        "POINTILLISM", "FAUVISM", "FUTURISM", "DADAISM", "POPART", "ARTNOUVEAU", "ARTDECO", "BRUTALISM",
        "GOTHIC", "ABSTRACT", "MODERNISM", "POSTMODERN", "SYMBOLISM", "PRIMITIVISM", "ROCOCO", "MANNERISM",
        "CLASSICISM", "CONCEPTUAL", "FOLKART", "OUTSIDERART",
        // Home decor styles
        "BOHEMIAN", "INDUSTRIAL", "FARMHOUSE", "MIDCENTURY", "RUSTIC", "COASTAL", "VICTORIAN", "TRADITIONAL",
        "ECLECTIC", "MINIMALIST", "TROPICAL", "SHABBYCHIC", "COTTAGECORE", "JAPANDI", "ARTISANAL", "VINTAGE",
        "RETRO", "GLAMOROUS", "MOODY", "MONOCHROME", "PASTORAL", "COUNTRYSIDE",
        // Art mediums & genres
        "SCULPTURE", "MOSAIC", "FRESCO", "TAPESTRY", "COLLAGE", "ETCHING", "ENGRAVING", "WOODCUT",
        "LINOCUT", "MURAL", "PORTRAIT", "LANDSCAPE", "STILLLIFE", "SKETCH", "DOODLE", "GRAFFITI",
        "CARVING", "POTTERY", "CERAMICS", "ORIGAMI", "CALLIGRAPHY", "ANIMATION", "PRINTMAKING", "SILKSCREEN",
        "EMBROIDERY", "QUILTING", "WEAVING", "KNITTING",
        // Flora - trees
        "BALDCYPRESS", "TAMARACK", "HEMLOCK", "JUNIPER", "YEW", "CYPRESS", "LARCH", "DOUGLASFIR",
        "COTTONWOOD", "BASSWOOD", "HORNBEAM", "SASSAFRAS", "TUPELO", "CATALPA", "LOCUST", "HACKBERRY",
        "BUCKEYE", "MULBERRY", "PAWPAW", "HAWTHORN", "CRABAPPLE", "ELDERBERRY", "REDBUD", "FRINGETREE",
        "SILVERBELL",
        // Flora - flowers
        "ROSE", "LILY", "IRIS", "DAISY", "POPPY", "PEONY", "DAHLIA", "ZINNIA",
        "COSMOS", "ASTER", "CARNATION", "HYDRANGEA", "HIBISCUS", "AZALEA", "CAMELLIA", "GARDENIA",
        "FREESIA", "RANUNCULUS", "ANEMONE", "CROCUS", "SNOWDROP", "BLUEBELL", "FOXGLOVE", "HOLLYHOCK",
        "DELPHINIUM", "LARKSPUR", "SNAPDRAGON", "CALENDULA", "COREOPSIS",
        // Flora - grasses & herbs
        "BASIL", "THYME", "OREGANO", "ROSEMARY", "SAGE", "MINT", "PARSLEY", "CILANTRO",
        "DILL", "CHIVES", "TARRAGON", "MARJORAM", "FENNEL", "BAYLEAF", "LEMONGRASS", "CHAMOMILE",
        "LAVENDER", "FEVERFEW", "YARROW", "NETTLE", "CLOVER", "TIMOTHY", "FESCUE", "RYEGRASS",
        "BLUEGRASS", "SEDGE", "BULRUSH", "PAPYRUS", "WHEATGRASS", "SWITCHGRASS",
        // Pigments & colors
        "CRIMSON", "COBALT", "OCHRE", "SEPIA", "INDIGO", "MAGENTA", "MAROON", "IVORY",
        "VERMILION", "CERULEAN", "AMBER", "MAUVE", "PERIWINKLE", "CHARTREUSE", "BURGUNDY", "TEAL",
        "SAFFRON", "UMBER",
        // Architecture
        "COLUMN", "ARCH", "DOME", "TURRET", "BALCONY", "VERANDA", "ATRIUM", "FACADE",
        "CORNICE", "PARAPET", "BUTTRESS", "PORTICO", "ROTUNDA", "MEZZANINE", "ALCOVE", "CORRIDOR",
        "VESTIBULE", "THRESHOLD", "LATTICE", "TRELLIS",
        // Kitchenware
        "SKILLET", "SAUCEPAN", "COLANDER", "LADLE", "SPATULA", "WHISK", "GRATER", "SIEVE",
        "TONGS", "PEELER", "ROLLINGPIN", "MORTAR", "PESTLE", "KETTLE", "TEAPOT", "TRIVET",
        "CASSEROLE", "RAMEKIN", "DECANTER",
        // Furniture (more)
        "FOOTREST", "PLANTER", "SHOERACK", "COATRACK", "WINEDRACK", "BEANBAG", "PORCHSWING", "GLIDER",
        "CHAIRRAIL", "WAINSCOT", "VALANCE", "PELMET",
        // Kitchenware (more)
        "STRAINER", "FUNNEL", "SPOON", "FORK", "KNIFE", "CLEAVER", "SHEARS", "APRON",
        "POTHOLDER", "OVENMITT", "DISHTOWEL", "PLACEMAT", "NAPKIN", "TABLECLOTH", "RUNNER", "CHOPSTICKS",
        "SKEWER", "TOOTHPICK", "STRAW", "COASTER", "CORKSCREW", "CANOPENER",
        // Architecture (more)
        "GABLE", "EAVE", "SHUTTER", "TRANSOM", "SKYLIGHT", "CHIMNEY", "MANTEL", "HEARTH",
        "STAIRCASE", "BANISTER", "RAILING", "LANDING", "FOYER", "PANTRY", "ATTIC", "BASEMENT",
        // Fabrics & textiles
        "COTTON", "LINEN", "SILK", "WOOL", "SATIN", "DENIM", "CORDUROY", "FLANNEL",
        "FLEECE", "CHIFFON", "ORGANZA", "TWEED", "SUEDE", "LEATHER", "BURLAP", "MUSLIN",
        "GAUZE", "LACE", "TAFFETA", "JERSEY", "SPANDEX", "POLYESTER",
        // Gardening
        "TROWEL", "SHOVEL", "RAKE", "HOE", "PRUNER", "WHEELBARROW", "WATERINGCAN", "GREENHOUSE",
        "COMPOST", "MULCH", "FERTILIZER", "TOPSOIL", "SEEDLING", "SPROUT",
        // Houseplants
        "POTHOS", "MONSTERA", "FERN", "IVY", "SNAKEPLANT", "ALOE", "BEGONIA", "VIOLET",
        "BROMELIAD", "CALATHEA", "PEPEROMIA", "ANTHURIUM", "DRACAENA", "YUCCA",
        // Crafting
        "STAPLER", "PUNCHER", "GLUEGUN", "TWINE", "BUTTON", "ZIPPER", "NEEDLE", "THIMBLE",
        "BOBBIN", "SPOOL", "PATTERN", "SEAM", "HEM", "STITCH",
        // Music genres
        "JAZZ", "BLUES", "FOLK", "REGGAE", "DISCO", "FUNK", "PUNK", "METAL",
        "GOSPEL", "SOUL", "SALSA", "MERENGUE", "SAMBA", "TANGO", "WALTZ", "POLKA",
        // Rooms & spaces
        "KITCHEN", "PARLOR", "STUDY", "LIBRARY", "NURSERY", "GARAGE", "CELLAR", "LOFT",
        "TERRACE", "PATIO", "COURTYARD", "GAZEBO", "PERGOLA", "SOLARIUM", "MUDROOM",
        // Textile arts
        "MACRAME", "CROCHET", "TATTING", "BATIK", "TIEDYE", "SCREENPRINT", "APPLIQUE", "PATCHWORK",
        // Decor accents
        "THROWPILLOW", "SLIPCOVER", "DRAPE", "CURTAIN", "BLINDS", "SCONCE", "CHANDELIER", "VASE",
        "URN", "FIGURINE", "BOOKEND", "WREATH", "GARLAND", "ORNAMENT", "MIRROR",
        // Flora (more)
        "ORCHID", "MARIGOLD", "ECHINACEA", "CONEFLOWER", "GOLDENROD", "MILKWEED", "THISTLE", "DANDELION",
        "CHICORY", "MULLEIN",
        // Miscellaneous
        "SUNDIAL", "BIRDBATH", "FOUNTAIN", "STATUE", "OBELISK", "PLINTH", "PEDESTAL", "NICHE",
        "CORNER", "TERRARIUM",
    )

    /**
     * [excludeWords] lets the caller avoid recently-used words (see WordSearchHistoryStore) so the
     * same handful of words don't keep reappearing just because the pool is being sampled randomly
     * each time. If exclusion would leave fewer candidates than [wordCount] (e.g. the whole bank has
     * cycled through), it falls back to the full bank rather than failing to generate a puzzle.
     */
    fun generate(
        size: Int = 11,
        wordCount: Int = 8,
        random: Random = Random.Default,
        excludeWords: Set<String> = emptySet(),
    ): WordSearchPuzzle {
        val eligible = WORD_BANK.filter { it.length <= size }
        val afterExclusion = eligible.filter { it !in excludeWords }
        val pool = if (afterExclusion.size >= wordCount) afterExclusion else eligible

        val chosenWords = pool
            .shuffled(random)
            .take(wordCount)
            .sortedByDescending { it.length } // place longer words first - they're harder to fit later

        val grid = Array(size) { CharArray(size) { ' ' } }
        val placed = mutableListOf<PlacedWord>()

        for (word in chosenWords) {
            placeWord(word, grid, size, random)?.let { placed.add(it) }
        }

        for (row in 0 until size) {
            for (col in 0 until size) {
                if (grid[row][col] == ' ') {
                    grid[row][col] = 'A' + random.nextInt(26)
                }
            }
        }

        return WordSearchPuzzle(size, grid, placed)
    }

    /**
     * Checks every legal position and direction for [word], and prefers whichever one
     * reuses the most letters already on the grid - that's what makes words actually cross
     * each other instead of just sitting in their own empty strip of the grid. Falls back to
     * placements with no overlap at all only when none exist, so every word still gets placed.
     */
    private fun placeWord(word: String, grid: Array<CharArray>, size: Int, random: Random): PlacedWord? {
        var bestOverlap = -1
        val bestCandidates = mutableListOf<PlacedWord>()

        for ((dRow, dCol) in DIRECTIONS) {
            val startRowRange = validStartRange(size, dRow, word.length)
            val startColRange = validStartRange(size, dCol, word.length)
            for (startRow in startRowRange) {
                for (startCol in startColRange) {
                    if (!fits(word, grid, startRow, startCol, dRow, dCol, size)) continue
                    val overlap = overlapCount(word, grid, startRow, startCol, dRow, dCol)
                    when {
                        overlap > bestOverlap -> {
                            bestOverlap = overlap
                            bestCandidates.clear()
                            bestCandidates.add(PlacedWord(word, startRow, startCol, dRow, dCol))
                        }
                        overlap == bestOverlap -> {
                            bestCandidates.add(PlacedWord(word, startRow, startCol, dRow, dCol))
                        }
                    }
                }
            }
        }

        if (bestCandidates.isEmpty()) return null

        // Several equally-good placements usually exist - picking randomly among them keeps
        // the grid from looking mechanically identical every time the same word comes up.
        val chosen = bestCandidates.random(random)
        for (i in word.indices) {
            grid[chosen.startRow + chosen.dRow * i][chosen.startCol + chosen.dCol * i] = word[i]
        }
        return chosen
    }

    private fun overlapCount(
        word: String,
        grid: Array<CharArray>,
        startRow: Int,
        startCol: Int,
        dRow: Int,
        dCol: Int,
    ): Int {
        var count = 0
        for (i in word.indices) {
            if (grid[startRow + dRow * i][startCol + dCol * i] == word[i]) count++
        }
        return count
    }

    private fun validStartRange(size: Int, delta: Int, length: Int): IntRange = when {
        delta == 0 -> 0 until size
        delta > 0 -> 0 until (size - (length - 1))
        else -> (length - 1) until size
    }

    private fun fits(
        word: String,
        grid: Array<CharArray>,
        startRow: Int,
        startCol: Int,
        dRow: Int,
        dCol: Int,
        size: Int,
    ): Boolean {
        for (i in word.indices) {
            val r = startRow + dRow * i
            val c = startCol + dCol * i
            if (r !in 0 until size || c !in 0 until size) return false
            val existing = grid[r][c]
            if (existing != ' ' && existing != word[i]) return false
        }
        return true
    }
}
