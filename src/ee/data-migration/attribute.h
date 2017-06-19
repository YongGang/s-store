/* Copyright (C) 2017 by S-Store Project
 * Brown University
 * Massachusetts Institute of Technology
 * Portland State University
 *
 * Author: S-Store Team (sstore.cs.brown.edu)
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

#ifndef ATTRIBUTE_H
#define ATTRIBUTE_H

//boost
//#define BOOST_LOG_DYN_LINK 1
//#include <boost/lexical_cast.hpp>
//#include <boost/type_index.hpp>
//#include <boost/log/core.hpp>
//#include <boost/log/trivial.hpp>
//#include <boost/log/expressions.hpp>
//namespace logging = boost::log;

#include <cstdio>
#include <stdint.h>
#include <assert.h>
#include <iostream>
#include <fstream>
#include "endianness.h"
#include "buffer.h"
#include "dataMigratorExceptions.h"
#include "common/NValue.hpp"
#include "common/ValuePeeker.hpp"

#define __STDC_FORMAT_MACROS
#include <inttypes.h>

class Attribute {

protected:
	/* can the argument be a NULL value */
	bool isNullable;

	/* how many bytes the attribute requires
	 this is only the number of real values (without any NULL values, for example, for char*)
	 but postgres can set it to -1 so this is why it is int and not unsigned int */
	int32_t bytesNumber;

	/* is this instance of the argument a NULL value or not */
	bool isNull;

	/* null values have to have null char '\0' on each position */
	static const int32_t nullValuesSize = 20;

	/* this is the declaration of null(s) for values that are empty (for example in SciDB binary format) - this is a string with only '\0' (null) values */
	static const char nullValues[nullValuesSize];

public:
	virtual Attribute* clone() const = 0;
	virtual ~Attribute() = 0;

	void setIsNullable(bool isNullable) {
		this->isNullable = isNullable;
		std::cout << "isNullable: " << this->isNullable;
	}
	bool getIsNullable() {
		return this->isNullable;
	}

	void setBytesNumber(int32_t bytesNumber) {
		this->bytesNumber = bytesNumber;
	}

	uint32_t getBytesNumber() {
		return this->bytesNumber;
	}

	void setIsNull(bool isNull) {
		this->isNull = isNull;
	}

	bool getIsNull() {
		return this->isNull;
	}
	/* if return value==0 then sucess, otherwise something went wrong */
	int virtual postgresReadBinary(FILE *fp) = 0;
	int virtual postgresReadBinaryBuffer(Buffer * buffer) = 0;
	//int virtual writeCsv(FILE *fp) = 0;
	int virtual writeCsv(std::ofstream & file) = 0;
	int virtual scidbWriteBinary(FILE *fp) = 0;
	int virtual scidbReadBinary(FILE *fp) = 0;
	int virtual postgresWriteBinary(FILE *fp) = 0;
	int virtual readSstore(const voltdb::NValue & nvalue) = 0;
};

Attribute* new_clone(Attribute const& other);

template<class T>
class GenericAttribute: public Attribute {
private:
	T value;
public:
	GenericAttribute(bool isNullable = false);
	~GenericAttribute() {
	}
	GenericAttribute<T>* clone() const {
		return new GenericAttribute<T>(*this);
	}
	void setValue(T value) {
		this->value = value;
	}
	T getValue() {
		return this->value;
	}
	int postgresReadBinary(FILE *fp);
	int postgresReadBinaryBuffer(Buffer * buffer);
	int writeCsv(std::ofstream & ofile);
	int scidbWriteBinary(FILE *fp);
	int scidbReadBinary(FILE *fp);
	int postgresWriteBinary(FILE *fp);
	int readSstore(const voltdb::NValue & nvalue);
};

// template specialization for strings (implemented as char*)

template<>
class GenericAttribute<char*> : public Attribute {
private:
	char* value;
	void freeValue();
public:
	GenericAttribute(bool isNullable = false);
	~GenericAttribute();
	GenericAttribute<char*>* clone() const {
		return new GenericAttribute<char*>(*this);
	}
	void setValue(char* value) {
		this->value = value;
	}
	char* getValue() {
		return this->value;
	}
	int postgresReadBinary(FILE *fp);
	int postgresReadBinaryBuffer(Buffer * buffer);
	int writeCsv(std::ofstream & ofile);
	int scidbWriteBinary(FILE *fp);
	int scidbReadBinary(FILE *fp);
	int postgresWriteBinary(FILE *fp);
	int readSstore(const voltdb::NValue & nvalue);
};

// template specialization for bool values
template<>
class GenericAttribute<bool> : public Attribute {
private:
	bool value;
public:
	GenericAttribute(bool isNullable = false);
	~GenericAttribute();
	GenericAttribute<bool>* clone() const {
		return new GenericAttribute<bool>(*this);
	}
	void setValue(bool value) {
		this->value = value;
	}
	bool getValue() {
		return this->value;
	}
	int postgresReadBinary(FILE *fp);
	int postgresReadBinaryBuffer(Buffer * buffer);
	int postgresWriteBinary(FILE *fp);
	int writeCsv(std::ofstream & ofile);
	int scidbWriteBinary(FILE *fp);
	int scidbReadBinary(FILE *fp);
	int readSstore(const voltdb::NValue& nvalue);
};

template<class T>
GenericAttribute<T>::GenericAttribute(bool isNullable) {
	this->isNullable = isNullable;
	this->bytesNumber = sizeof(T);
}

template<class T>
int GenericAttribute<T>::postgresReadBinary(FILE *fp) {
	uint32_t valueBytesNumber;
	if (fread(&valueBytesNumber, 4, 1, fp) != 1)
		exit(1);
	valueBytesNumber = be32toh(valueBytesNumber);
	if (valueBytesNumber == -1) {
		// this is null and there are no bytes for the value
		this->isNull = true;
		return 0;
	}
	this->isNull = false;
	assert(valueBytesNumber == this->bytesNumber);
	if (fread(&value, this->bytesNumber, 1, fp) != 1)
		exit(1);
	// change the endianness
	endianness::fromBigEndianToHost < T > (value);
	return 0; // success
}

template<class T>
int GenericAttribute<T>::scidbReadBinary(FILE *fp) {
	//std::cout << "this is type: " << boost::typeindex::type_id().pretty_name() << std::endl;
	if (this->isNullable) {
		/* read 1 byte that tells us if the attribute is null or not:
		 - If a nullable attribute contains a non-null value,
		 the preceding null byte is -1.
		 - If a nullable attribute contains a null value,
		 the preceding null byte will contain the missing reason code,
		 which must be between 0 and 127
		 */
		int8_t nullValue;
		size_t bytes_read;
		bytes_read = fread(&nullValue, 1, 1, fp);
		if (bytes_read < 1)
			return 1; // no more data in the input file
//		if (nullValue >= 0 && nullValue <= 127) {
			this->isNull = true;
			// we don't need the reason why it is null so we'll write byte 0
			/* A fixed-length data type that allows null values
			 will always consume one more byte than the datatype requires,
			 regardless of whether the actual value is null or non-null.
			 For example, an int8 will require 2 bytes and an int64
			 will require 9 bytes. (In the figure, see bytes 2-4 or 17-19.)
			 */
//		} else {
			/* if nullValue != -1: it means that there was another unexpected value
//			 different from [-1,127] */
//			assert(nullValue == -1);
//		}
	}
	size_t elements_read = fread(&(this->value), this->bytesNumber, 1, fp);
	//BOOST_LOG_TRIVIAL(debug) << "elements_read: " << elements_read;
	//BOOST_LOG_TRIVIAL(debug) << "bytes number in the attribute: " << this->bytesNumber;
	//BOOST_LOG_TRIVIAL(debug) << "value: " << this->value;
	if (elements_read != 1)
		return 1; // no more data in the input file
	return 0; // everything is correct: success
}

template<class T>
int GenericAttribute<T>::writeCsv(std::ofstream & ofile) {
	// write nothing if the value is NULL
	if (this->isNull)
		return 0;
	// write the value only if it's not NULL
	// fprintf(fp,"%" PRId64,this->value);
	ofile << value;
	return 0;
}

/**
 * To write properly int8_t to a csv file it has to be cast to at least
 * a small int (otherwise strange characters appear).
 *
 */
template<>
int GenericAttribute<int8_t>::writeCsv(std::ofstream & ofile);

template<class T>
int GenericAttribute<T>::scidbWriteBinary(FILE *fp) {
	if (this->isNullable) {
		if (this->isNull) {
			// we don't know the reason why it is null so we'll write byte 0
			char nullReason = 0;
			if (fwrite(&nullReason, 1, 1, fp) != 1)
				exit(1);
			assert(nullValuesSize >= this->bytesNumber);
			if (fwrite(nullValues, this->bytesNumber, 1, fp) != 1)
				exit(1);
			return 0;
		} else {
			char notNull = -1;
			if (fwrite(&notNull, 1, 1, fp) != 1)
				exit(1);
		}
	}
	if (fwrite(&(this->value), this->bytesNumber, 1, fp) != 1)
		exit(1);
	return 0;
}

template<class T>
int GenericAttribute<T>::postgresWriteBinary(FILE *fp) {
	//BOOST_LOG_TRIVIAL(debug) << "postgresWriteBinary is nullable: " << this->isNullable;
	if (this->isNullable) {
		if (this->isNull) {
			/* -1 indicates a NULL field value */
			int32_t nullValue = -1;
			/* this works in place */
			endianness::fromHostToBigEndian < int32_t > (nullValue);
			if (fwrite(&nullValue, 4, 1, fp) != 1)
				exit(1);
			/* No value bytes follow in the NULL case. */
			return 0;
		}
	}
	uint32_t attrLengthPostgres = htobe32(this->bytesNumber);
	if (fwrite(&attrLengthPostgres, 4, 1, fp) != 1)
		exit(1);
	endianness::fromHostToBigEndian < T > (this->value);
	//BOOST_LOG_TRIVIAL(debug) << "postgresWriteBinary bytes number: " << this->bytesNumber;
	if (fwrite(&(this->value), this->bytesNumber, 1, fp) != 1)
		exit(1);
	return 0;
}

template<class T>
int GenericAttribute<T>::postgresReadBinaryBuffer(Buffer * buffer) {
	//fprintf(stderr,"%s\n","Read binary buffer");
	uint32_t valueBytesNumber;
	BufferRead(&valueBytesNumber, 4, 1, buffer);
	valueBytesNumber = be32toh(valueBytesNumber);
	//printf("type size read: %d\n",valueBytesNumber);
	//printf("expected type size: %d\n",this->bytesNumber);
	if (valueBytesNumber == -1) {
		// this is null and there are no bytes for the value
		this->isNull = true;
		return 0;
	}
	this->isNull = false;
	assert(valueBytesNumber == this->bytesNumber);
	BufferRead(&value, this->bytesNumber, 1, buffer);
	endianness::fromBigEndianToHost < T > (value);
	//std::cout << "value read: " << value << std::endl;
	return 0; // success
}

template<> int GenericAttribute<int8_t>::readSstore(
		const voltdb::NValue& nvalue);
template<> int GenericAttribute<int16_t>::readSstore(
		const voltdb::NValue& nvalue);
template<> int GenericAttribute<int32_t>::readSstore(
		const voltdb::NValue& nvalue);
template<> int GenericAttribute<int64_t>::readSstore(
		const voltdb::NValue& nvalue);
template<> int GenericAttribute<float>::readSstore(
		const voltdb::NValue& nvalue);
template<> int GenericAttribute<double>::readSstore(
		const voltdb::NValue& nvalue);

/** template specialization for timestamp data type */
class Timestamp {
private:
	int64_t timestamp;
public:
	int64_t getTimestamp() {
		return timestamp;
	}
	void setTimestamp(int64_t timestamp) {
		this->timestamp = timestamp;
	}
};

template<> int GenericAttribute<Timestamp>::readSstore(
		const voltdb::NValue& nvalue);

template<> int GenericAttribute<Timestamp>::writeCsv(std::ofstream & ofile);

template<>
int GenericAttribute<Timestamp>::postgresWriteBinary(FILE *fp);

template<>
int GenericAttribute<Timestamp>::scidbWriteBinary(FILE *fp);

/** The int8_t type from S-store is not supported by PostgreSQL, so this type
 * will be mapped to small int.
 */
template<>
int GenericAttribute<int8_t>::postgresWriteBinary(FILE *fp);

/** template specialization for decimal data type */
class Decimal {
private:
	std::string decimalValueString;
public:
	std::string getDecimalString() {
		return decimalValueString;
	}

	void setDecimal(voltdb::NValue decimalValue) {
		this->decimalValueString = voltdb::ValuePeeker::peekDecimalString(
				decimalValue);
	}
};

template<> int GenericAttribute<Decimal>::readSstore(
		const voltdb::NValue& nvalue);

template<> int GenericAttribute<Decimal>::writeCsv(std::ofstream & ofile);

#endif // ATTRIBUTE_H

