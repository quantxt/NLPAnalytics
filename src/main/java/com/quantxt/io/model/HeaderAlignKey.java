package com.quantxt.io.model;
public class HeaderAlignKey {

        private final String name;
        private final int index;
        private final boolean source;

        public HeaderAlignKey(String name, int index, boolean source) {
            this.name = name;
            this.index = index;
            this.source = source;
        }

        public String getName() {
            return name;
        }

        public int getIndex() {
            return index;
        }

        public boolean isSource() {
            return source;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + index;
            result = prime * result + ((name == null) ? 0 : name.hashCode());
            result = prime * result + (source ? 1231 : 1237);
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            HeaderAlignKey other = (HeaderAlignKey) obj;
            if (index != other.index)
                return false;
            if (name == null) {
                if (other.name != null)
                    return false;
            } else if (!name.equals(other.name))
                return false;
            if (source != other.source)
                return false;
            return true;
        }

        @Override
        public String toString() {
            return "[name=" + name + ", index=" + index
                    + ", source=" + source + "]";
        }

    }