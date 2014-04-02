/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package example.stores;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import lombok.extern.slf4j.Slf4j;

import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.mapping.FieldSetMapper;
import org.springframework.batch.item.file.separator.DefaultRecordSeparatorPolicy;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.batch.item.file.transform.FieldSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.index.GeospatialIndex;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindException;

/**
 * @author Oliver Gierke
 */
@Slf4j
@Component
public class StoreInitializer {

	@Autowired
	public StoreInitializer(StoreRepository repository, MongoOperations operations) throws Exception {

		operations.indexOps(Store.class).ensureIndex(new GeospatialIndex("address.location"));

		if (repository.count() != 0) {
			return;
		}

		List<Store> stores = readStores();
		log.info("Importing {} stores into MongoDB…", stores.size());
		repository.save(stores);
		log.info("Successfully imported {} stores.", repository.count());
	}

	public static List<Store> readStores() throws Exception {

		ClassPathResource resource = new ClassPathResource("starbucks.csv");
		Scanner scanner = new Scanner(resource.getInputStream());
		String line = scanner.nextLine();
		scanner.close();

		FlatFileItemReader<Store> itemReader = new FlatFileItemReader<Store>();
		itemReader.setResource(resource);

		// DelimitedLineTokenizer defaults to comma as its delimiter
		DefaultLineMapper<Store> lineMapper = new DefaultLineMapper<Store>();

		DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer();
		tokenizer.setNames(line.split(","));
		tokenizer.setStrict(false);

		lineMapper.setLineTokenizer(tokenizer);
		lineMapper.setFieldSetMapper(StoreFieldSetMapper.INSTANCE);
		itemReader.setLineMapper(lineMapper);
		itemReader.setRecordSeparatorPolicy(new DefaultRecordSeparatorPolicy());
		itemReader.setLinesToSkip(1);
		itemReader.open(new ExecutionContext());

		List<Store> stores = new ArrayList<>();
		Store store = null;

		do {

			store = itemReader.read();

			if (store != null) {
				stores.add(store);
			}

		} while (store != null);

		return stores;
	}

	static enum StoreFieldSetMapper implements FieldSetMapper<Store> {

		INSTANCE;

		/* 
		 * (non-Javadoc)
		 * @see org.springframework.batch.item.file.mapping.FieldSetMapper#mapFieldSet(org.springframework.batch.item.file.transform.FieldSet)
		 */
		@Override
		public Store mapFieldSet(FieldSet fields) throws BindException {

			Point location = new Point(fields.readDouble("Longitude"), fields.readDouble("Latitude"));
			Address address = new Address(fields.readString("Street Address"), fields.readString("City"),
					fields.readString("Zip"), location);

			return new Store(fields.readString("Name"), address);
		}
	}
}
